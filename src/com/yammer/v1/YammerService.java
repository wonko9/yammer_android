package com.yammer.v1;

import com.yammer.v1.YammerData.YammerDataException;
import com.yammer.v1.YammerProxy.YammerProxyException;
import com.yammer.v1.models.Message;
import com.yammer.v1.models.Network;
import com.yammer.v1.settings.SettingsEditor;
import com.yammer.v1.YammerProxy;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

public class YammerService extends Service {

  private static final boolean DEBUG = G.DEBUG;

  public static final String INTENT_RESET_ACCOUNT = "com.yammer.v1:RESET_ACCOUNT";
  
  public static final String INTENT_POST_MESSAGE = "com.yammer.v1:POST_MESSAGE";
  public static final String EXTRA_MESSAGE = "message";
  
  public static final String INTENT_AUTHENTICATION_COMPLETE = "com.yammer.v1:AUTHENTICATION_COMPLETE";
  public static final String EXTRA_TOKEN = "token";

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
      if (INTENT_RESET_ACCOUNT.equals(intent.getAction())) {
        // Acquire sempahore to disallow updates
        if ( !jsonUpdateSemaphore.tryAcquire() ) {
          if (DEBUG) Log.d(getClass().getName(), "Could not acquire permit to update semaphore - aborting");
          return;
        }
        
        reset();
        
        // Allow updates again (if authorized)
        jsonUpdateSemaphore.release();
        sendBroadcast(YammerActivity.INTENT_MUST_AUTHENTICATE_DIALOG);
      } else if (INTENT_POST_MESSAGE.equals(intent.getAction())) {
        /*
         * Usually called from external something like the browser
         * when a user tries to share something.
         */
        String message = intent.getExtras().getString(EXTRA_MESSAGE);
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
      } else if(INTENT_AUTHENTICATION_COMPLETE.equals(intent.getAction())) {
          updateCurrentUserData();
          authenticationComplete();
      }
    }

    private void authenticationComplete() {
      if (DEBUG) Log.d(getClass().getName(), ".authenticationComplete");
      YammerProxy proxy = getYammerProxy();
      setAuthorized(true);
      yammerData.createNetwork(getCurrentNetworkId(), getCurrentUserId(), proxy.requestToken, proxy.tokenSecret);
      sendBroadcast(new Intent(YammerActivity.INTENT_AUTHORIZATION_DONE));
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
      if (null == network || null == network.accessToken || null == network.accessTokenSecret) {
        sendBroadcast(YammerActivity.INTENT_MUST_AUTHENTICATE_DIALOG);        		        	
      } else {
        if (DEBUG) Log.d("Yammer", "DB (accessToken): " + network.accessToken);
        if (DEBUG) Log.d("Yammer", "DB (accessTokenSecret): " + network.accessTokenSecret);
        
        // Store token secrets to NWOAuth to be able to authorize
        getYammerProxy().requestToken = network.accessToken;
        getYammerProxy().tokenSecret = network.accessTokenSecret;
        
        // There is already an access token available, so we are authorized
        setAuthorized(true);
      }

      registerIntents();	        

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

    }
  }

  private void registerIntents() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(INTENT_RESET_ACCOUNT);
    filter.addAction(INTENT_POST_MESSAGE);
    filter.addAction(INTENT_AUTHENTICATION_COMPLETE);
    registerReceiver(new YammerIntentReceiver(), filter);
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
    sendBroadcast(YammerActivity.INTENT_PUBLIC_TIMELINE_UPDATED);
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

  public void updateCurrentUserData() {
    String userData = null;
    try {
      userData = getYammerProxy().accessResource(getURLBase() + "/api/v1/users/current.json");
    } catch (YammerProxy.YammerProxyException e) {
      e.printStackTrace();
      Toast.makeText(this, "Unable to get user data", Toast.LENGTH_LONG);
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
      
      // Save user and network ID as the default user/network
      getSettings().setDefaultUserId(currentUserId);
      getSettings().setDefaultNetworkId(currentNetworkId);

    } catch (JSONException e) {
      Toast.makeText(this, "Unable to parse user data", Toast.LENGTH_LONG);
      e.printStackTrace();
    }
  }

  public void clearMessages() {
    yammerData.clearMessages();
  }
  
  public void updatePublicMessages(boolean reloading) {
    if (DEBUG) Log.i(getClass().getName(), "Updating public timeline");
    
    if ( ! isAuthorized() ) {
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
          } catch( JSONException e ) {
            if (DEBUG) Log.w(getClass().getName(), e.getMessage());
          }
        }
      } catch (JSONException e) {
        if (DEBUG) Log.w(getClass().getName(), e.getMessage());
      } catch (YammerDataException e) {
        if (DEBUG) Log.w(getClass().getName(), e.getMessage());
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
      } catch (JSONException e) {
        if (DEBUG) Log.w(getClass().getName(), e.getMessage());
      }			

    } catch (YammerProxyException e) {
      if (DEBUG) Log.w(getClass().getName(), e.getMessage());
      return;
    } catch (JSONException e) {
      if (DEBUG) Log.w(getClass().getName(), e.getMessage());
      return;
    } catch (YammerDataException e) {
      if (DEBUG) Log.w(getClass().getName(), e.getMessage());
      return;
    } catch (Exception e) {
       e.printStackTrace();
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
      sendBroadcast(YammerActivity.INTENT_PUBLIC_TIMELINE_UPDATED);
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

  private void sendBroadcast(String _intent) {
    sendBroadcast(new Intent(_intent));
  }

  private YammerProxy yammerProxy;

  private void resetYammerProxy() {
    this.yammerProxy = null;
  }
  private YammerProxy getYammerProxy() {
    if (null == this.yammerProxy) {
      this.yammerProxy = YammerProxy.getYammerProxy(getApplicationContext());
    }
    return this.yammerProxy;
  }

  //TODO: Refactor these statics to instance methods
  public static void setAuthorized(boolean authorized) {
    YammerService.authorized = authorized;
  }

  public static boolean isAuthorized() {
    return authorized;
  }
  
}
