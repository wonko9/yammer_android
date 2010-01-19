package com.yammer.v1;

import com.yammer.v1.YammerData.YammerDataException;
import com.yammer.v1.models.Message;
import com.yammer.v1.models.Network;
import com.yammer.v1.settings.SettingsEditor;
import com.yammer.v1.YammerProxy;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

public class YammerService extends Service {

  private static final boolean DEBUG = G.DEBUG;

  /** Client states **/
  private static int STATE_RAW = -1;
  private static int STATE_INITIALIZED = 0;		
  private static int CLIENT_STATE = STATE_RAW;

  /** Notification types **/
  private static int NOTIFICATION_NEW_MESSAGE = 0;
  //private static int NOTIFICATION_ERROR = 1;
  private static int NOTIFICATION_APPLICATION_UPDATE = 2;

  // Are we authorized?
  private static boolean authorized = false;
  // Check if an update should be made every 35 seconds
  private final long GLOBAL_UPDATE_INTERVAL = 10500;
  private static long lastUpdateTime = 0;
  // Check for application updates once a day
  private static long APPLICATION_UPDATE_INTERVAL = 1000*60*60*24;
  private Timer timer = new Timer();
  // Maintained JSON objects
  JSONObject jsonMessages = null;
  // Default feed
  int defaultFeedId = 0; /* 0 - All messages */
  // Properties of the current network
  int newMessageCount = 0;
  long currentUserId = 0;
  long currentNetworkId = 0;
  // Database
  public YammerData yammerData = null;
  // Semaphone to control write access to json objects above
  private final Semaphore jsonUpdateSemaphore = new Semaphore(1);
  private Boolean bound = false;
  private final IBinder mBinder = new YammerBinder();
  // Authorization thread
  YammerAuthorizeThread authThread = null;
  // Set to true, when authentication is in progress
  public static boolean isAuthenticating = false;
  private static String authenticationToken;
  // Wakelock
  PowerManager.WakeLock wakelock = null; 

  SettingsEditor settings;
  private SettingsEditor getSettings() {
    if(null == this.settings) {
      this.settings = new SettingsEditor(getApplicationContext());
    }
    return this.settings;
  }

  /**
   * Class for clients to access.  Because we know this service always
   * runs in the same process as its clients, we don't need to deal with
   * IPC.
   */
  public class YammerBinder extends Binder {
    YammerService getService() {
      return YammerService.this;
    }
  }

  class YammerIntentReceiver extends BroadcastReceiver {
    public YammerIntentReceiver() {
    }
    @Override
    public void onReceive(Context context, Intent intent) {
      if (DEBUG) Log.d(getClass().getName(), "Intent received: " + intent.getAction());
      if ( intent.getAction().equals("com.yammer.v1:RESET_ACCOUNT") ) {
        // Acquire sempahore to disallow updates
        if ( !jsonUpdateSemaphore.tryAcquire() ) {
          if (DEBUG) Log.d(getClass().getName(), "Could not acquire permit to update semaphore - aborting");
          return;
        }
        
        reset();
        
        // Allow updates again (if authorized)
        jsonUpdateSemaphore.release();
        sendBroadcast("com.yammer.v1:MUST_AUTHENTICATE_DIALOG");
      } else if ( intent.getAction().equals("com.yammer.v1:POST_MESSAGE" )) {
        /*
         * Usually called from external something like the browser
         * when a user tries to share something.
         */
        Bundle bundle = intent.getExtras();
        String message = bundle.getString("message");
        try {
          // message ID is 0 since this is not a reply
          getYammerProxy().postMessage(message, 0);
        } catch (YammerProxy.AccessDeniedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (YammerProxy.ConnectionProblem e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    
    private void reset() {
      yammerData.resetData(getCurrentNetworkId());
      getSettings().setDefaultUserId(0L);
      getSettings().setDefaultNetworkId(0L);
      resetYammerProxy();
      YammerService.setAuthorized(false);
    }
  };

  @Override
  public void onCreate() {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onCreate");
    yammerData = new YammerData(this);
    super.onCreate();
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
  }

  /**
   * Set the internal state of the client
   * @param newState
   */
  public void setClientState(final int newState) {
    // Change the state
    YammerService.CLIENT_STATE = newState;
    // Invoke GUI callback to notify that a change has occured
    // Invoke...
  }

  class YammerAuthorizeThread extends Thread {

    YammerService service = null;

    YammerAuthorizeThread(YammerService service) {
      this.service = service;
    }

    public void run() {
      if (DEBUG) Log.d(getClass().getName(), "Requesting request token");
      try {
        // Only continue if not already authenticating
        if ( isAuthenticating == true ) {
          return;
        }
        isAuthenticating = true;
        if ( DEBUG ) Log.d(getClass().getName(), "YammerService bound to Yammer: " + bound);
        // Yes, bound.. So Have the Yammer activity show a progress bar
        sendBroadcast("com.yammer.v1:AUTHORIZATION_START" );
        // Get request token
        // Fetch the request token and token secret
        getYammerProxy().getRequestToken();	
        // Make sure that a request token and a secret token was received
        String responseUrl = getYammerProxy().authorizeUser();
        if ( DEBUG ) Log.d(getClass().getName(), "Response URL received: " + responseUrl);
        // Send an intent that will start the browser
        Intent intent = new Intent( "com.yammer.v1:AUTHORIZATION_BROWSER" );
        intent.putExtra("responseUrl", responseUrl);
        sendBroadcast(intent);
        // Wait for user to finish authorization
        if (DEBUG) Log.d(getClass().getName(), "Waiting for user to finish authorization");
        while ( YammerService.isAuthorized() == false ) {
          // Sleep 10 ms
          Thread.sleep(10);
        }
        if (DEBUG) Log.d(getClass().getName(), "User done with authorization");
        getYammerProxy().enableApplication(YammerService.authenticationToken);
        // We need to update the current user data
        updateCurrentUserData();
        
        // Save user and network ID as the default user/network
        getSettings().setDefaultUserId(currentUserId);
        getSettings().setDefaultNetworkId(currentNetworkId);
        
        // It seems authorization was a success, so store the token and secret
        yammerData.createNetwork(getCurrentNetworkId(), getCurrentUserId(), getYammerProxy().requestToken, getYammerProxy().tokenSecret);				
        // Authorization done, so the progress bar can be removed
        sendBroadcast(new Intent("com.yammer.v1:AUTHORIZATION_DONE"));
        // Authorized done, so network requests towards the
        // Yammer network can start
        setAuthorized(true);
        // 
        isAuthenticating = false;				
      } catch ( YammerProxy.ConnectionProblem e ) {
        // Send an intent to the Yammer activity notifying about the error
        sendBroadcast("com.yammer.v1:NETWORK_ERROR_FATAL");        		
      } catch ( Exception e ) {
        Log.d(getClass().getName(), "An exception occured: " + e.toString());
        e.printStackTrace();
      } finally {
        isAuthenticating = false;
      }			
    }
  }


  /**
   * Start the authorization
   */
  public void initiateAuthorization() {
    if ( DEBUG ) Log.d(getClass().getName(), "YammerService.initiateAuthorization");
    authThread = new YammerAuthorizeThread(this);
    authThread.start();
  }

  /**
   * Stop authorization
   */
  public void cancelAuthorization() {
    if ( DEBUG ) Log.d(getClass().getName(), "YammerService.cancelAuthorization: authThread = " + authThread);
    if (authThread != null) {
      try {
        isAuthenticating = false;
        authThread.interrupt();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void onStart(Intent intent, int startId) {
    super.onStart(intent, startId);				
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onStart");
    if (DEBUG) Log.d(getClass().getName(), "Client state: " + YammerService.CLIENT_STATE);
    // Was the service already started?
    if ( YammerService.CLIENT_STATE != STATE_RAW ) {
      if (DEBUG) Log.d(getClass().getName(), "YammerService already started once, so just return");
      // Just return
      return;
    } else {
      if (DEBUG) Log.i(getClass().getName(), "Yammer service is initializing");
      // Service has been started once and considered initialized
      YammerService.CLIENT_STATE = STATE_INITIALIZED;
      // Start authorization with Yammer
      if (DEBUG) Log.d(getClass().getName(), "Fetching request token from Yammer");
      
      // Try to load the access token from the shared preferences
      currentUserId = getSettings().getDefaultUserId();	        	        
      if (DEBUG) Log.d("Yammer", "DB (currentUserId): " + currentUserId);
      currentNetworkId = getSettings().getDefaultNetworkId();
      if (DEBUG) Log.d("Yammer", "DB (currentNetworkId): " + currentNetworkId);
      
      Network network = yammerData.getNetwork(getCurrentNetworkId());
      
      // Check if a valid access token was stored at some point
      if ((null == network || null == network.accessToken || null == network.accessTokenSecret) && !isAuthenticating ) {
        sendBroadcast("com.yammer.v1:MUST_AUTHENTICATE_DIALOG");        		        	
      } else {
        if (DEBUG) Log.d("Yammer", "DB (accessToken): " + network.accessToken);
        if (DEBUG) Log.d("Yammer", "DB (accessTokenSecret): " + network.accessTokenSecret);
        
        // Store token secrets to NWOAuth to be able to authorize
        getYammerProxy().requestToken = network.accessToken;
        getYammerProxy().tokenSecret = network.accessTokenSecret;
        
        // There is already an access token available, so we are authorized
        setAuthorized(true);
      }

      IntentFilter filter = new IntentFilter();
      filter.addAction("com.yammer.v1:RESET_ACCOUNT");
      filter.addAction("com.yammer.v1:POST_MESSAGE");
      registerReceiver(new YammerIntentReceiver(), filter);	        

      // Start the update timer
      timer.scheduleAtFixedRate(
          new TimerTask() {
            public void run() {
              try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                // How long to wait
                long updateTimeout = getSettings().getUpdateTimeout();
                //if (DEBUG) Log.d(getClass().getName(), "updateTimeout: " + (lastUpdateTime + updateTimeout) + ", currentTime: " + System.currentTimeMillis());
                // Is it time to update?
                if ( updateTimeout != 0 && 
                    (System.currentTimeMillis() > lastUpdateTime + updateTimeout) ) {
                  if (DEBUG) Log.d(getClass().getName(), "Acquiring wakelock");
                  wakelock.acquire();
                  // Time to update
                  updatePublicMessages(false);
                  lastUpdateTime = System.currentTimeMillis();		        					
                } 
              } catch (Exception e) {
                if (DEBUG) Log.d(getClass().getName(), "An exception occured during updatePublicMessage()");
                e.printStackTrace();
              } finally {
                wakelock.release();
                if (DEBUG) Log.d(getClass().getName(), "Wakelock released");
              }
            }
          }, 0, GLOBAL_UPDATE_INTERVAL
      );

      // Start timer to check for application updates
      // Start the update timer
      timer.scheduleAtFixedRate(
          new TimerTask() {
            public void run() {
              try {
                // Check for new versions of the application
                checkForApplicationUpdate();
              } catch (Exception e) {
                if (DEBUG) Log.d(getClass().getName(), "An exception occured during checkForApplicationUpdate()");
                e.printStackTrace();
              }
            }
          }, 0, APPLICATION_UPDATE_INTERVAL
      );
      // If this is a beta, then check for updates every hour
      if (G.IS_BETA) {
        APPLICATION_UPDATE_INTERVAL = 1000*60*60*1;
      }
    }
  }

  /**
   * Reset counter holding number of new messages
   */
  public void resetMessageCount() {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.resetMessageCount");
    newMessageCount = 0;
  }

  /**
   * Notify user about new activity or errors
   */
  public void notifyUser(int _message_id, int type) {
    // Only notify when we are not bound to the activity
    if ( type == NOTIFICATION_NEW_MESSAGE && bound == true ) {
      return;
    }

    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    // Default icon
    int icon = R.drawable.yammer_notification_icon;
    if ( type == NOTIFICATION_APPLICATION_UPDATE ) {
      icon = R.drawable.yammer_logo_small;			
    }

    Notification notification = new Notification(icon,
        getResources().getString(_message_id), 
        System.currentTimeMillis()
    ); 
    notification.ledARGB = 0xff035c99; 
    notification.ledOnMS = 200; 
    notification.ledOffMS = 200; 
    notification.defaults = Notification.DEFAULT_SOUND;        
    notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL; 

    // Vibrate enabled?
    if(getSettings().getVibrate()) {
      notification.vibrate = new long[] {0, 100, 100, 100, 100, 100};	        	
    }

    if ( type == NOTIFICATION_NEW_MESSAGE ) {
      // Intent of this notification - launch yammer activity
      Intent intent = new Intent(this, YammerActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
      // Only show number of new messages if more than one
      if ( newMessageCount > 1 ) {
        notification.number = newMessageCount;	        	
      }
      notification.setLatestEventInfo(this, 
          getResources().getString(R.string.new_yammer_message),
          getResources().getQuantityString(R.plurals.new_messages_available, newMessageCount, newMessageCount),
          pendingIntent
      );
      
    } else if ( type == NOTIFICATION_APPLICATION_UPDATE ) {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:"+getPackageName()));
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
      notification.setLatestEventInfo(this,
          getResources().getString(R.string.application_update_title),
          getResources().getString(R.string.application_update_text),
          pendingIntent
       );
    }

    if (DEBUG) Log.d(getClass().getName(), "Displaying notification - " + newMessageCount + " new messages!");
    nm.notify(R.string.app_name, notification);
  }

  /**
   * Post a message or a reply to the current Yammer Network
   * 
   * @param message - message to post
   * @param messageId - Message being replied to
   * 
   * @throws YammerProxy.AccessDeniedException
   * @throws YammerProxy.ConnectionProblem 
   */
  public void postMessage(final String message, final long messageId) throws YammerProxy.YammerProxyException {
    getYammerProxy().postMessage(message, messageId);
  }

  /**
   * Delete a message from the current Yammer network
   * @param messageId - Delete the message with the given ID
   * @throws YammerProxy.AccessDeniedException
   * @throws YammerProxy.ConnectionProblem 
   */
  public void deleteMessage(final long messageId) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".deleteMessage");
    // TODO: change to getYammer().deleteMessage(messageId);
    getYammerProxy().deleteResource(getURLBase() + "/api/v1/messages/"+messageId);		
    yammerData.deleteMessage(messageId);
    // TODO: sendBroadcast(ACTION_MESSAGE_DELETED, messageId);
    sendBroadcast("com.yammer.v1:PUBLIC_TIMELINE_UPDATED");
  }

  public void followUser(final long userId) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.followUser");
    // GET https://yammer.com/api/v1/subscriptions/to_user/<id>.json
    if (DEBUG) Log.d(getClass().getName(), "Following user");
    getYammerProxy().followUser(userId);
    if (DEBUG) Log.d(getClass().getName(), "User followed!");
  }

  public void unfollowUser(final long userId) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".followUser");
    getYammerProxy().unfollowUser(userId);
  }

  public long getCurrentUserId() {
    // URL: https://www.yammer.com/api/v1/users/current.xml
    return currentUserId;
  }

  public long getCurrentNetworkId() {
    return currentNetworkId;
  }

  public void updateCurrentUserData() throws YammerProxy.YammerProxyException {
    String url = getURLBase() + "/api/v1/users/current.json";
    // Fetch the user data JSON
    String userData = null;
    try {
      userData = getYammerProxy().accessResource(url);
    } catch (YammerProxy.ConnectionProblem e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
      return;
    }
    // If json public timeline doesn't exist, create it
    try {
      JSONObject jsonUserData = new JSONObject(userData);
      
      currentUserId = jsonUserData.getLong("id");
      if (DEBUG) Log.d(getClass().getName(), "Current user ID: " + currentUserId);
      
      currentNetworkId = jsonUserData.getLong("network_id");
      if (DEBUG) Log.d(getClass().getName(), "Current network ID: " + currentNetworkId);

      if (DEBUG) Log.d(getClass().getName(), "Updating follow status from references");
      
      // Populate feeds table
      JSONArray jsonArray = jsonUserData.getJSONObject("web_preferences").getJSONArray("home_tabs");
      if (DEBUG) Log.d(getClass().getName(), "Found " + jsonArray.length() + " feeds");    		
      yammerData.clearFeeds();
      for( int ii=0; ii < jsonArray.length(); ii++ ) {
        try {
          yammerData.addFeed(jsonArray.getJSONObject(ii));
        } catch( YammerData.YammerDataException e ) {
          if (DEBUG) Log.w(getClass().getName(), e.getMessage());
          e.printStackTrace();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // TODO: remove (handled by market)
  protected void checkForApplicationUpdate() {
    String url = getURLBase() + "/application_support/android/updates";
    try {
      if (DEBUG) Log.d(getClass().getName(), "Querying URL "+url+" for new updates");
      DefaultHttpClient httpClient = new DefaultHttpClient(); 
      HttpGet httpGet = new HttpGet(url);
      HttpResponse httpResponse = httpClient.execute(httpGet);
      String response = EntityUtils.toString(httpResponse.getEntity());
      if (DEBUG) Log.d(getClass().getName(), "Read HTTP respone: " + response);
      // Parse the respone
      String[] responseSplit = response.split(":");
      int versionCode = Integer.parseInt(responseSplit[1].trim());
      if (DEBUG) Log.d(getClass().getName(), "Version code read: " + versionCode);
      // Get the versioncode from manifest
      PackageManager pm = getPackageManager();
      PackageInfo pi = pm.getPackageInfo(this.getPackageName(), 0);
      if ( versionCode > pi.versionCode ) {
        // Notify user about new version
        if (DEBUG) Log.d(getClass().getName(), "New version is available. Notifying user.");
        notifyUser(R.string.application_update_title, NOTIFICATION_APPLICATION_UPDATE);
      }
    } catch (Exception e) {
      if (DEBUG) Log.d(getClass().getName(), "Error while checking for application updates");
      e.printStackTrace();
    }
  }

  public void clearMessages() {
    yammerData.clearMessages();
  }
  
  public void updatePublicMessages(boolean reloading) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.i(getClass().getName(), "Updating public timeline");
    
    if ( isAuthorized() == false ) {
      if (DEBUG) Log.i(getClass().getName(), "User not authorized - skipping update");
      return;
    }
    
    boolean notificationRequired = false;
    boolean messagesFound = false;
    
    try {
      if ( !jsonUpdateSemaphore.tryAcquire() ) {
        if (DEBUG) Log.d(getClass().getName(), "Could not acquire permit to update semaphore - aborting");
        return;
      }
      
      String messages = getNewerMessages();

      if (DEBUG) Log.d(getClass().getName(), "Messages JSON: " + messages);
      jsonMessages = new JSONObject(messages);

      try {
        if (DEBUG) Log.d(getClass().getName(), "Updating users from references");
        JSONArray references = jsonMessages.getJSONArray("references");
        for( int ii=0; ii < references.length(); ii++ ) {
          try {
            JSONObject reference = references.getJSONObject(ii);
            if(reference.getString("type").equals("user")) {
              yammerData.addUser(reference);
            }
          } catch( Exception e ) {
            if (DEBUG) Log.w(getClass().getName(), e.getMessage());
          }
        }
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }			

      try {
        if (DEBUG) Log.d(getClass().getName(), "Updating messages");
        // Retrieve all messages
        JSONArray jsonArray = jsonMessages.getJSONArray("messages");
        // Add all fetched messages tp the database
        for( int ii=0; ii < jsonArray.length(); ii++ ) {
          // Add the message reference to the database
          Message message = yammerData.addMessage(jsonArray.getJSONObject(ii), getCurrentNetworkId());
          // Is this my own message?
          boolean ownMessage = getCurrentUserId() == message.userId;
          // Only ask if notification is required if none of
          // the previous messages had notification requirement
          if (false == notificationRequired) {
            notificationRequired = !ownMessage;
            if (DEBUG) Log.d(getClass().getName(), "Notification required: " + notificationRequired);
          }
          // Only increment message counter if this is not one of our own messages
          if ( !ownMessage ) {
            // If we reach this point, a new message has been received - increment new message counter
            newMessageCount ++;	        				
          }
          messagesFound = true;
        }
        
        getSettings().setUpdatedAt();
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }			

    } catch (Exception e) {
      if (DEBUG) Log.e(getClass().getName(), "An error occured while parsing JSON: " + e.getStackTrace());
      return;
    } finally {
      // Release the semaphore
      jsonUpdateSemaphore.release();
    }

    if (messagesFound) {
      // Is notification required?
      if (notificationRequired && !reloading) {
        // Yep, so notify the user with a notification icon
        notifyUser(R.string.new_yammer_message, NOTIFICATION_NEW_MESSAGE);				
      }
      sendBroadcast("com.yammer.v1:PUBLIC_TIMELINE_UPDATED");
    }
  }

  private String getNewerMessages() throws YammerProxy.YammerProxyException, YammerDataException {
    return getYammerProxy().accessResource(getFeedURL()+".json?newer_than="+yammerData.getLastMessageId(getCurrentNetworkId()));
  }

  private String getFeedURL() throws YammerData.YammerDataException {
    return this.yammerData.getURLForFeed(getSettings().getFeed());
  }

  private String getURLBase() {
    if (OAuthCustom.BASE_URL != null)
      return OAuthCustom.BASE_URL;
    return getSettings().getUrl();
  }

  @Override
  public void onDestroy() {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onDestroy");
    super.onDestroy();
    // It seems we were destroyed for some reason, so set the state to raw
    YammerService.CLIENT_STATE = STATE_RAW;
    // onStart will now trigger a reauthorization
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onBind");
    this.bound = true;
    return mBinder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onUnbind");
    this.bound = false;
    // Don't invoke onRebind, so return false
    return false;
  }


  private YammerProxy yammerProxy;

  private void resetYammerProxy() {
    this.yammerProxy = null;
  }
  private YammerProxy getYammerProxy() {
    if (null == this.yammerProxy) {
      this.yammerProxy = new YammerProxy(getURLBase());
    }
    return this.yammerProxy;
  }

  public static void setAuthorized(boolean authorized) {
    YammerService.authorized = authorized;
  }

  public static void setAuthenticationToken(String token) {
    YammerService.authenticationToken = token;
  }

  public static boolean isAuthorized() {
    return authorized;
  }
  
  private void sendBroadcast(String _intent) {
    sendBroadcast(new Intent(_intent));
  }

}
