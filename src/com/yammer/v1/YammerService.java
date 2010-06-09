package com.yammer.v1;

import com.yammer.v1.YammerData.YammerDataException;
import com.yammer.v1.YammerProxy.YammerProxyException;
import com.yammer.v1.models.Feed;
import com.yammer.v1.models.Message;
import com.yammer.v1.models.Network;
import com.yammer.v1.models.User;
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
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
  
  public static final String INTENT_ENABLE_NOTIFICATION = "com.yammer.v1:ENABLE_NOTIFICATION";
  public static final String INTENT_DISABLE_NOTIFICATION = "com.yammer.v1:DISABLE_NOTIFICATION";
  
  public static final String INTENT_CHANGE_NETWORK = "com.yammer.v1:CHANGE_NETWORK";
  public static final String EXTRA_NETWORK_ID = "network_id";

  public static final String INTENT_CHANGE_FEED = "com.yammer.v1:CHANGE_FEED";
  public static final String EXTRA_FEED_NAME = "feed_name";

  /** Client states **/
  private static int STATE_RAW = -1;
  private static int STATE_INITIALIZED = 0;		
  private static int CLIENT_STATE = STATE_RAW;

  /** Notification types **/
  private static int NOTIFICATION_NEW_MESSAGE = 0;

  // Are we authorized?
  private static boolean authorized = false;
  // Check if an update should be made every 10.5 seconds
  private final long GLOBAL_UPDATE_INTERVAL = 10500;
  private static long lastUpdateTime = 0;
  // Check for application updates once a day
  private Timer timer = new Timer();
  
  // Properties of the current network
  int newMessageCount = 0;
  
  // Semaphone to control write access to json objects above
  private final Semaphore jsonUpdateSemaphore = new Semaphore(1);
  private final IBinder mBinder = new YammerBinder();
  
  private boolean notificationEnabled = true;
  
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
    public void onReceive(Context context, final Intent intent) {
      if (DEBUG) Log.d(getClass().getName(), "Intent received: " + intent.getAction());
      if (INTENT_RESET_ACCOUNT.equals(intent.getAction())) {
        // Acquire sempahore to disallow updates
        if ( !jsonUpdateSemaphore.tryAcquire() ) {
          if (DEBUG) Log.d(getClass().getName(), "Could not acquire permit to update semaphore - aborting");
          return;
        }
        
        resetAccount();
        
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
          authenticationComplete();
          updateCurrentUserData();
          
      } else if(INTENT_ENABLE_NOTIFICATION.equals(intent.getAction())) {
          YammerService.this.notificationEnabled = true;
          
      } else if(INTENT_DISABLE_NOTIFICATION.equals(intent.getAction())) {
        YammerService.this.notificationEnabled = false;

      } else if(INTENT_CHANGE_NETWORK.equals(intent.getAction())) {
        new Thread() {
          public void run() {
            changeNetwork(intent.getLongExtra(EXTRA_NETWORK_ID, 0L));
          }
        }.start();
      } else if(INTENT_CHANGE_FEED.equals(intent.getAction())) {
        new Thread() {
          public void run() {
            changeFeed(intent.getStringExtra(EXTRA_FEED_NAME));
          }
        }.start();
      }
    }

    private void authenticationComplete() {
      if (DEBUG) Log.d(getClass().getName(), ".authenticationComplete");
      setAuthorized(true);
      sendBroadcast(new Intent(YammerActivity.INTENT_AUTHORIZATION_DONE));
   }

    private void resetAccount() {
      getYammerData().resetData(getCurrentNetworkId());
      setCurrentNetworkId(0L);
      resetYammerProxy();
      YammerService.setAuthorized(false);
    }
  };

  @Override
  public void onCreate() {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onCreate");
    super.onCreate();
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
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

      if (null == getCurrentNetwork()) {
        sendBroadcast(YammerActivity.INTENT_MUST_AUTHENTICATE_DIALOG);        		        	
      } else {
        setAuthorized(true);
        getYammerProxy().setCurrentNetwork(getCurrentNetwork());
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
                if (updateTimeout != 0 && (System.currentTimeMillis() > lastUpdateTime + updateTimeout) ) {
                  if (DEBUG) Log.d(getClass().getName(), "Acquiring wakelock");
                  wakelock.acquire();
                  // Time to update
                  getMessages(false);
                  lastUpdateTime = System.currentTimeMillis();		        					
                  wakelock.release();
                  if (DEBUG) Log.d(getClass().getName(), "Wakelock released");
                }
              } catch (RuntimeException e) {
                if (DEBUG) Log.d(getClass().getName(), "An exception occured during updatePublicMessage()");
                e.printStackTrace();
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
    filter.addAction(INTENT_ENABLE_NOTIFICATION);
    filter.addAction(INTENT_DISABLE_NOTIFICATION);
    filter.addAction(INTENT_CHANGE_NETWORK);
    filter.addAction(INTENT_CHANGE_FEED);

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
    if(NOTIFICATION_NEW_MESSAGE == type && !notificationEnabled) {
      return;
    }

    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    // Default icon
    int icon = R.drawable.yammer_notification_icon;

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

    if(NOTIFICATION_NEW_MESSAGE == type) {
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
    getYammerData().deleteMessage(messageId);
    // TODO: sendBroadcast(ACTION_MESSAGE_DELETED, messageId);
    sendBroadcast(YammerActivity.INTENT_PUBLIC_TIMELINE_UPDATED);
  }

  public void followUser(long userId) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".followUser: " + userId);
    getYammerProxy().followUser(userId);
  }

  public void unfollowUser(long userId) throws YammerProxy.YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".unfollowUser: " + userId);
    getYammerProxy().unfollowUser(userId);
  }

  private void changeNetwork(long _id) {
    if (DEBUG) Log.d(getClass().getName(), "changeNetwork: " + _id);
    setCurrentNetworkId(_id);
    toastUser(R.string.changing_network_text, getCurrentNetwork().name);
    updateCurrentUserData();
// handled by intent fired from updateCurrentUserData => reloadNetworks => reloadFeeds     
//    clearMessages();
//    getMessages(true);
  }
  
  private void changeFeed(String _name) {
    if (DEBUG) Log.d(getClass().getName(), "changeFeed: " + _name);
    toastUser(R.string.changing_feed_text, _name);
    getSettings().setFeed(_name);
    clearMessages();
    getMessages(true);
  }

  public void updateCurrentUserData() {
    if (DEBUG) Log.i(getClass().getName(), ".updateCurrentUserData");
    try {
      User user = getYammerProxy().getCurrentUser(true);
      getYammerData().saveUsers(user.followedUsers);
      reloadNetworks();
    } catch (YammerProxyException ex) {
      ex.printStackTrace();
    }
  }

  private void reloadNetworks() {
    if (DEBUG) Log.i(getClass().getName(), ".reloadNetworks");
    try {
      getYammerData().clearNetworks();
      Network[] networks = getYammerProxy().getNetworks();
      if(0L == getCurrentNetworkId()) {
        setCurrentNetworkId(networks[0].networkId);
      }
      getYammerData().addNetworks(networks);
    } catch(YammerProxyException ex) {
      ex.printStackTrace();
    }
    reloadFeeds();
  }

  private void reloadFeeds() {
    if (DEBUG) Log.i(getClass().getName(), ".reloadFeeds");
    try {
      //getYammerData().deleteFeedsFor(getCurrentNetworkId());
      getYammerData().clearFeeds();
      Feed[] feeds = getYammerProxy().getFeeds();
      getYammerData().addFeeds(feeds);
      
      Intent intent = new Intent(YammerService.INTENT_CHANGE_FEED);
      intent.putExtra(YammerService.EXTRA_FEED_NAME, feeds[0].name);
      sendBroadcast(intent);
    } catch(YammerProxyException ex) {
      ex.printStackTrace();
    }
  }
  
  public void clearMessages() {
    getYammerData().clearMessages();
  }
  
  public void getMessages(boolean reloading) {
    if (DEBUG) Log.i(getClass().getName(), ".getMessages");
    
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
      
      String messages = getYammerProxy().getMessagesNewerThan(getFeedURL(), getYammerData().getLastMessageId(getCurrentNetworkId()));

      if (DEBUG) Log.d(getClass().getName(), "Messages JSON: " + messages);
      JSONObject jsonMessages = new JSONObject(messages);

      try {
        if (DEBUG) Log.d(getClass().getName(), "Updating users from references");
        JSONArray references = jsonMessages.getJSONArray("references");
        for( int ii=0; ii < references.length(); ii++ ) {
          try {
            JSONObject reference = references.getJSONObject(ii);
            if(reference.getString("type").equals("user")) {
              getYammerData().addUser(reference);
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
      
//      try {
//        if (DEBUG) Log.d(getClass().getName(), "Trying to fetch last_seen_message_id");
//        JSONObject meta = jsonMessages.getJSONObject("meta");
//        getSettings().setLastSeenMessageId(meta.getLong("last_seen_message_id"));
//      } catch (JSONException e) {
//        if (DEBUG) Log.w(getClass().getName(), e.getMessage());
//      }     

      try {
        if (DEBUG) Log.d(getClass().getName(), "Updating messages");
        Network network = getCurrentNetwork();
        JSONArray jsonArray = jsonMessages.getJSONArray("messages");
        for( int ii=0; ii < jsonArray.length(); ii++ ) {
          Message message = getYammerData().addMessage(jsonArray.getJSONObject(ii), getCurrentNetworkId());
      
          if(message.messageId > network.lastMessageId) {
            network.lastMessageId = message.messageId;
          }
          
          // Is this my own message?
          boolean ownMessage = getCurrentUserId() == message.userId;
          // Only ask if notification is required if none of
          // the previous messages had notification requirement
          if(!notificationRequired) {
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
        getYammerData().save(network);
        
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
      setCurrentNetworkId(0L);
      reloadNetworks();
    } finally {
      // Release the semaphore
      jsonUpdateSemaphore.release();
    }

    if (messagesFound) {
      if (notificationRequired && !reloading) {
        notifyUser(R.string.new_yammer_message, NOTIFICATION_NEW_MESSAGE);				
      }
      
      sendBroadcast(YammerActivity.INTENT_PUBLIC_TIMELINE_UPDATED);
    }
  }

  private String getFeedURL() throws YammerData.YammerDataException {
    return getYammerData().getURLForFeed(getCurrentNetworkId(), getSettings().getFeed());
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
    return mBinder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    if (DEBUG) Log.d(getClass().getName(), "YammerService.onUnbind");
    // Don't invoke onRebind, so return false
    return false;
  }

  private void sendBroadcast(String _intent) {
    sendBroadcast(new Intent(_intent));
  }

  private YammerProxy mYammerProxy;

  private void resetYammerProxy() {
    if(null != this.mYammerProxy) {
      this.mYammerProxy.reset();
      this.mYammerProxy = null;
    }
  }
  private YammerProxy getYammerProxy() {
    if (null == this.mYammerProxy) {
      this.mYammerProxy = YammerProxy.getYammerProxy(getApplicationContext());
    }
    return this.mYammerProxy;
  }

  //TODO: Refactor these statics to instance methods
  public static void setAuthorized(boolean authorized) {
    YammerService.authorized = authorized;
  }

  public static boolean isAuthorized() {
    return authorized;
  }

  private YammerData mYammerData = null;
  //TODO: privatize
  YammerData getYammerData() {
    if(null == mYammerData) {
      mYammerData = new YammerData(this);
    }
    return mYammerData; 
  }

  private long mCurrentNetworkId = 0L;
  
  private void setCurrentNetworkId(long _id) {
    mCurrentNetworkId = _id;
    mCurrentNetwork = null;
    getSettings().setCurrentNetworkId(_id);
    if(null != getCurrentNetwork()) {
      getYammerProxy().setCurrentNetwork(getCurrentNetwork());
    }
  }
 
  //TODO: privatize
  long getCurrentNetworkId() {
    if(0L == mCurrentNetworkId) { 
      mCurrentNetworkId = getSettings().getCurrentNetworkId();
    }
    return mCurrentNetworkId;
  }

  private Network mCurrentNetwork;
  private Network getCurrentNetwork() {
    if(null == mCurrentNetwork) { 
      mCurrentNetwork = getYammerData().getNetwork(getCurrentNetworkId());
    }
    return mCurrentNetwork;
  }

  //TODO: privatize
  long getCurrentUserId() {
    return getCurrentNetwork().userId;
  }

  private void toastUser(final int _resId, final Object... _args) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      public void run() {
        Toast.makeText(getApplicationContext(), String.format(getText(_resId).toString(), _args), Toast.LENGTH_LONG).show();
      }
    });
  }
  
   
}