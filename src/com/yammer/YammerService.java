package com.yammer;

import static com.yammer.YammerDataConstants.MESSAGE_ID;

import static com.yammer.YammerDataConstants.TABLE_MESSAGES;

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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

public class YammerService extends Service {
	
	public static boolean DEBUG = true;

	private static final String TAG_YSERVICE = "YammerService";
	private static final String PREFS_NAME = "YammerPrefs";
	
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
	JSONObject jsonPublicTimeline = null;
	// Default feed
	int defaultFeedId = 0; /* 0 - All messages */
	// Properties of the current network
	long lastMessageId = 0;
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
    		if (G.DEBUG) Log.d(TAG_YSERVICE, "Intent received: " + intent.getAction());
        	if ( intent.getAction().equals("com.yammer:RESET_ACCOUNT") ) {
    			// Acquire sempahore to disallow updates
        		if ( !jsonUpdateSemaphore.tryAcquire() ) {
    				if (G.DEBUG) Log.d(TAG_YSERVICE, "Could not acquire permit to update semaphore - aborting");
    				return;
    			}
        		// Remove all data from the database
        		yammerData.resetData(getCurrentNetworkId());
        		// Reset the account related preferences
		        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);				
		        SharedPreferences.Editor editor = settings.edit();
		        editor.putLong("DefaultUserId", 0);
		        editor.putLong("DefaultNetworkId", 0);
		        editor.commit();
		        // Reset last message Id
		        YammerService.this.lastMessageId = 0;
		        // Reset the OAuth library
		        resetOAuth();
		        // Not authorized anymore
		        YammerService.setAuthorized(false);
		        // Release semaphore and allow updates again (if authorized)
		        jsonUpdateSemaphore.release();
		        // Show the authenticate dialog
	    		Intent authenticateIntent = new Intent( "com.yammer:MUST_AUTHENTICATE_DIALOG" );
	    		sendBroadcast(authenticateIntent);
        	} else if ( intent.getAction().equals("com.yammer:POST_MESSAGE" )) {
				/*
				 * Usually called from external something like the browser
				 * when a user tries to share something.
				 */
        		Bundle bundle = intent.getExtras();
				String message = bundle.getString("message");
				try {
					// message ID is 0 since this is not a reply
					postMessage(message, 0);
				} catch (NWOAuthAccessDeniedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NWOAuthConnectionProblem e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
        	}
        }
    };
		
	@Override
	public void onCreate() {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::onCreate");
        yammerData = new YammerData(this);
		super.onCreate();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG_YSERVICE);
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
		private static final String TAG_YSERVICETHREAD = "YammerServiceThread";
		
		YammerService service = null;
		
		YammerAuthorizeThread(YammerService service) {
			this.service = service;
		}
		
		public void run() {
			if (G.DEBUG) Log.d(TAG_YSERVICETHREAD, "Requesting request token");
			try {
				// Only continue if not already authenticating
				if ( isAuthenticating == true ) {
					return;
				}
				isAuthenticating = true;
				if ( G.DEBUG ) Log.d(TAG_YSERVICE, "YammerService bound to Yammer: " + bound);
				// Yes, bound.. So Have the Yammer activity show a progress bar
				Intent intent = new Intent( "com.yammer:AUTHORIZATION_START" );
				sendBroadcast(intent);
				// Get request token
				// Fetch the request token and token secret
				getOAuth().getRequestToken();	
				// Make sure that a request token and a secret token was received
				String responseUrl = getOAuth().authorizeUser();
				if ( G.DEBUG ) Log.d(TAG_YSERVICE, "Response URL received: " + responseUrl);
				// Send an intent that will start the browser
				intent = new Intent( "com.yammer:AUTHORIZATION_BROWSER" );
				intent.putExtra("responseUrl", responseUrl);
				//intent.putExtra("responseUrl", "http://nullwire.com/test/test.php");
				sendBroadcast(intent);
				// Wait for user to finish authorization
				if (G.DEBUG) Log.d(TAG_YSERVICETHREAD, "Waiting for user to finish authorization");
				while ( YammerService.isAuthorized() == false ) {
					// Sleep 10 ms
					Thread.sleep(10);
				}
				if (G.DEBUG) Log.d(TAG_YSERVICETHREAD, "User done with authorization");
				getOAuth().enableApplication(YammerService.authenticationToken);
				// We need to update the current user data
				updateCurrentUserData();
				// Set this user and network ID as the default user/network
		        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);				
		        SharedPreferences.Editor editor = settings.edit();
		        editor.putLong("DefaultUserId", currentUserId);
		        editor.putLong("DefaultNetworkId", currentNetworkId);
		        editor.commit();
		        // TODO: Who am I following?
				// It seems authorization was a success, so store the token and secret
				yammerData.createNetwork(getCurrentNetworkId(), getCurrentUserId(), getOAuth().requestToken, getOAuth().tokenSecret);				
				// Authorization done, so the progress bar can be removed
				intent = new Intent( "com.yammer:AUTHORIZATION_DONE" );
				sendBroadcast(intent);
				// Authorized done, so network requests towards the
				// Yammer network can start
				setAuthorized(true);
				// 
				isAuthenticating = false;				
			} catch ( NWOAuthConnectionProblem e ) {
        		// Send an intent to the Yammer activity notifying about the error
    			Intent intent = new Intent( "com.yammer:NETWORK_ERROR_FATAL" );
    			sendBroadcast(intent);        		
			} catch ( Exception e ) {
				Log.d(TAG_YSERVICETHREAD, "An exception occured: " + e.toString());
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
		if ( G.DEBUG ) Log.d(TAG_YSERVICE, "YammerService::initiateAuthorization");
    	authThread = new YammerAuthorizeThread(this);
    	authThread.start();
	}
	
	/**
	 * Stop authorization
	 */
	public void cancelAuthorization() {
		if ( G.DEBUG ) Log.d(TAG_YSERVICE, "YammerService::cancelAuthorization: authThread = " + authThread);
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
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::onStart");
		if (G.DEBUG) Log.d(TAG_YSERVICE, "Client state: " + YammerService.CLIENT_STATE);
		// Was the service already started?
		if ( YammerService.CLIENT_STATE != STATE_RAW ) {
			if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService already started once, so just return");
			// Just return
			return;
		} else {
			if (G.DEBUG) Log.i(TAG_YSERVICE, "Yammer service is initializing");
			// Service has been started once and considered initialized
			YammerService.CLIENT_STATE = STATE_INITIALIZED;
			// Start authorization with Yammer
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Fetching request token from Yammer");
	        // Try to load the access token from the shared preferences
	        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
	        currentUserId = settings.getLong("DefaultUserId", 0);	        	        
	        currentNetworkId = settings.getLong("DefaultNetworkId", 0);
	        // Get the default feed ID
	        // In the following if userId and networkId are 0, it should result in null for accessToken and accessTokenSecret
	        // which should again trigger the authentication process
	        String accessToken = null;
	        String accessTokenSecret = null;
	        try {
	        	lastMessageId = yammerData.getLastMessageId(getCurrentNetworkId());	        	        
	        	accessToken = yammerData.getAccessToken(getCurrentNetworkId(), getCurrentUserId());
	        	accessTokenSecret = yammerData.getAccessTokenSecret(getCurrentNetworkId(), getCurrentUserId());
	        } catch (Exception e) {
	        	e.printStackTrace();
	        	lastMessageId = 0;
	        }
	       	// Retrieve the last message ID for the current network
	        if (G.DEBUG) Log.d("Yammer", "DB (lastMessageId): " + lastMessageId);
	        if (G.DEBUG) Log.d("Yammer", "DB (currentUserId): " + currentUserId);
	        if (G.DEBUG) Log.d("Yammer", "DB (currentNetworkId): " + currentNetworkId);
	        if (G.DEBUG) Log.d("Yammer", "DB (accessToken): " + accessToken);
	        if (G.DEBUG) Log.d("Yammer", "DB (accessTokenSecret): " + accessTokenSecret);
	        // Check if a valid access token was stored at some point
	        if ( (accessToken == null || accessTokenSecret == null) && !isAuthenticating ) {
	        	// No access token present, so notify yammer activity that authentication
	        	// should be done first.
	    		Intent authenticateIntent = new Intent( "com.yammer:MUST_AUTHENTICATE_DIALOG" );
	    		sendBroadcast(authenticateIntent);        		        	
	        } else {
	        	// Store token secrets to NWOAuth to be able to authorize
	        	getOAuth().requestToken = accessToken;
	        	getOAuth().tokenSecret = accessTokenSecret;
	        	// There is already an access token available, so we are authorized
	        	setAuthorized(true);
	        }
	        
	        IntentFilter filter = new IntentFilter();
	        filter.addAction("com.yammer:RESET_ACCOUNT");
	        filter.addAction("com.yammer:POST_MESSAGE");
	        registerReceiver(new YammerIntentReceiver(), filter);	        
	        
	        // Start the update timer
	        timer.scheduleAtFixedRate(
	        		new TimerTask() {
		        		public void run() {
		        			try {
		        				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		        				// How long to wait
		        				long updateTimeout = YammerSettings.getUpdateTimeout(YammerService.this);
		        				//if (G.DEBUG) Log.d(TAG_YSERVICE, "updateTimeout: " + (lastUpdateTime + updateTimeout) + ", currentTime: " + System.currentTimeMillis());
		        				// Is it time to update?
		        				if ( updateTimeout != 0 && 
		        						(System.currentTimeMillis() > lastUpdateTime + updateTimeout) ) {
			        				if (G.DEBUG) Log.d(TAG_YSERVICE, "Acquiring wakelock");
			        				wakelock.acquire();
			        				// Time to update
			        				updatePublicMessages();
			        				lastUpdateTime = System.currentTimeMillis();		        					
		        				} 
		        			} catch (Exception e) {
		        				if (G.DEBUG) Log.d(TAG_YSERVICE, "An exception occured during updatePublicMessage()");
		        				e.printStackTrace();
		        			} finally {
		        				wakelock.release();
		        				if (G.DEBUG) Log.d(TAG_YSERVICE, "Wakelock released");
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
		        				if (G.DEBUG) Log.d(TAG_YSERVICE, "An exception occured during checkForApplicationUpdate()");
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
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::resetMessageCount");
		newMessageCount = 0;
	}
	
    /**
     * Notify user about new activity or errors
     */
    public void notifyUser(String message, int type) {
    	// Only notify when we are not bound to the activity
    	if ( type == NOTIFICATION_NEW_MESSAGE && bound == true ) {
    		return;
    	}
		
    	NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		// Default icon
		int icon = R.drawable.yammer_notification_icon;
		if ( type == NOTIFICATION_APPLICATION_UPDATE ) {
			icon = R.drawable.yowl_icon_small;			
		}
		
		Notification notification = new Notification(icon, message, System.currentTimeMillis()); 
        notification.ledARGB = 0xff035c99; 
        notification.ledOnMS = 200; 
        notification.ledOffMS = 200; 
        notification.defaults = Notification.DEFAULT_SOUND;        
        notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL; 
        
        // Vibra enabled?
        if (YammerSettings.getVibrate(this)) {
	        notification.vibrate = new long[] {0, 100, 100, 100, 100, 100};	        	
        }
        
        if ( type == NOTIFICATION_NEW_MESSAGE ) {
        	// Intent of this notification - launch yammer activity
        	Intent intent = new Intent(this, Yammer.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            // Only show number of new messages if more than one
            if ( newMessageCount > 1 ) {
    	        notification.number = newMessageCount;	        	
            }
            String contentText = getResources().getQuantityString(R.plurals.new_messages_available, newMessageCount, newMessageCount);
            notification.setLatestEventInfo(this, getResources().getString(R.string.new_yammer_message), contentText, pendingIntent);        	
        } else if ( type == NOTIFICATION_APPLICATION_UPDATE ) {
        	// Launch market - search for Nullwire
        	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:"+getPackageName()));
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            notification.setLatestEventInfo(this, "Application Update", "A new version of Yowl is available.", pendingIntent);
        }

        if (G.DEBUG) Log.d(TAG_YSERVICE, "Displaying notification - " + newMessageCount + " new messages!");
        nm.notify(R.string.app_name, notification);
    }

    /**
     * Post a message or a reply to the current Yammer Network
     * @param message - message to post
     * @param messageId - Message being replied to
     * @throws NWOAuthAccessDeniedException
     * @throws NWOAuthConnectionProblem 
     */
	public void postMessage(final String message, final long messageId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::postMessage");
		// URL POST: https://www.yammer.com/api/v1/messages/
		// replied_to_id - If this is a reply
		// body - Body of message
		// Start deletion on network in thread
		if (G.DEBUG) Log.d(TAG_YSERVICE, "Posting message");
		getOAuth().postResource(getURLBase() + "/api/v1/messages/", message, messageId);
		Intent intent = new Intent( "com.yammer:PUBLIC_TIMELINE_UPDATED" );
		sendBroadcast(intent);
	}
	
	/**
	 * Delete a message from the current Yammer network
	 * @param messageId - Delete the message with the given ID
	 * @throws NWOAuthAccessDeniedException
	 * @throws NWOAuthConnectionProblem 
	 */
	public void deleteMessage(final long messageId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::deleteMessage");
		// Start deletion on network in thread
		getOAuth().deleteResource(getURLBase() + "/api/v1/messages/"+messageId);		
		// Delete it from the database
		SQLiteDatabase dbDelete = yammerData.getWritableDatabase();
		int count = dbDelete.delete(TABLE_MESSAGES, MESSAGE_ID+"="+messageId, null);
		if (G.DEBUG) Log.d(TAG_YSERVICE, "Items deleted: " + count);
		// It seems we were able to delete the message send an intent to update the timeline
		Intent intent = new Intent( "com.yammer:PUBLIC_TIMELINE_UPDATED" );
		sendBroadcast(intent);
	}
	
	public void followUser(final long userId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::followUser");
		// GET https://yammer.com/api/v1/subscriptions/to_user/<id>.json
		if (G.DEBUG) Log.d(TAG_YSERVICE, "Following user");
		getOAuth().followUser(userId);
		if (G.DEBUG) Log.d(TAG_YSERVICE, "User followed!");
		// Update the database to reflect change
		yammerData.subscribe(YammerData.ID_TARGET_USER, userId, true);		
	}
	
	public void unfollowUser(final long userId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::followUser");
		// DELETE https://yammer.com/api/v1/subscriptions/
		if (G.DEBUG) Log.d(TAG_YSERVICE, "Following user");
		getOAuth().unfollowUser(userId);
		if (G.DEBUG) Log.d(TAG_YSERVICE, "User followed!");
		// Update the database to reflect change
		yammerData.subscribe(YammerData.ID_TARGET_USER, userId, false);		
	}

	public long getCurrentUserId() {
		// URL: https://www.yammer.com/api/v1/users/current.xml
		return currentUserId;
	}
	
	public long getCurrentNetworkId() {
		return currentNetworkId;
	}

	public void updateCurrentUserData() throws NWOAuthAccessDeniedException {
		String url = getURLBase() + "/api/v1/users/current.json";
		// Fetch the user data JSON
		String userData = null;
		try {
			userData = getOAuth().accessResource(url);
		} catch (NWOAuthConnectionProblem e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		// If json public timeline doesn't exist, create it
		try {
			JSONObject jsonUserData = new JSONObject(userData);
			currentUserId = jsonUserData.getLong("id");
			currentNetworkId = jsonUserData.getLong("network_id");
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Current user ID: " + currentUserId);
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Current network ID: " + currentNetworkId);

			if (G.DEBUG) Log.d(TAG_YSERVICE, "Updating follow status from references");
			// Retrieve all references to users
    		JSONArray jsonArray = jsonUserData.getJSONArray("subscriptions");
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Found " + jsonArray.length() + " subscriptions");    		
			// Unfollow all users first
			yammerData.subscribeReset(getCurrentNetworkId());
			// Add all fetched messages tp the database
    		for( int i=0; i < jsonArray.length(); i++ ) {
    			try {
    				JSONObject subscription = jsonArray.getJSONObject(i);
    				// Check that we are looking at a user object
    				if ( !subscription.getString("type").equals("user") ) {
    					continue;
    				}
    				// Subscribe to this user
    				yammerData.subscribe(YammerData.ID_TARGET_USER, subscription.getLong("id"), true);
    			} catch( Exception e ) {
    				if (G.DEBUG) Log.w(TAG_YSERVICE, e.getMessage());
    				e.printStackTrace();
        		}
    		}
		} catch (Exception e) {
			e.printStackTrace();			
		}
	}

	// Check for updates
	protected void checkForApplicationUpdate() {
		String url = getURLBase() + "/android/updates/";
		try {
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Querying URL "+url+" for new updates");
			DefaultHttpClient httpClient = new DefaultHttpClient(); 
			HttpGet httpGet = new HttpGet(url);
			HttpResponse httpResponse = httpClient.execute(httpGet);
			String response = EntityUtils.toString(httpResponse.getEntity());
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Read HTTP respone: " + response);
			// Parse the respone
			String[] responseSplit = response.split(":");
			int versionCode = Integer.parseInt(responseSplit[1]);
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Version code read: " + versionCode);
			// Get the versioncode from manifest
			PackageManager pm = getPackageManager();
			PackageInfo pi = pm.getPackageInfo(this.getPackageName(), 0);
			if ( versionCode > pi.versionCode ) {
				// Notify user about new version
				if (G.DEBUG) Log.d(TAG_YSERVICE, "New version is available. Notifying user.");
				notifyUser("There is a new version available of Yowl. Click this message to update.", NOTIFICATION_APPLICATION_UPDATE);
			}
		} catch (Exception e) {
			if (G.DEBUG) Log.d(TAG_YSERVICE, "Error while checking for application updates");
			e.printStackTrace();
		}
	}
	
	public void updatePublicMessages() throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
		if (G.DEBUG) Log.i(TAG_YSERVICE, "Updating public timeline");		
		if ( isAuthorized() == false ) {
			if (G.DEBUG) Log.i(TAG_YSERVICE, "User not authorized - skipping update");
			// Notify Yammer activity that it is time to show a authorize dialog			
			return;
		}
		// Only when a message from another user is detected in the network
		// the notifcation should be shown.
		boolean notificationRequired = false;
		// Sets to true if new messages found
		boolean timelineUpdated = false; 
		try {
			if ( !jsonUpdateSemaphore.tryAcquire() ) {
				if (G.DEBUG) Log.d(TAG_YSERVICE, "Could not acquire permit to update semaphore - aborting");
				return;
			}
			// Fetch the public timeline
			String url = null;
			if (YammerSettings.getDefaultFeed(this).equals("my_feed")) {
				url = getURLBase() + "/api/v1/messages/following.json?newer_than="+lastMessageId;				
			} else {
				url = getURLBase() + "/api/v1/messages.json?newer_than="+lastMessageId;				
			}
			String timeline = null;
			timeline = getOAuth().accessResource(url);

			if (G.DEBUG) Log.d(TAG_YSERVICE, "Public timeline JSON: " + timeline);
			// If json public timeline doesn't exist, create it
			jsonPublicTimeline = new JSONObject(timeline);
			
			try {
				if (G.DEBUG) Log.d(TAG_YSERVICE, "Updating users from references");
				// Retrieve all references to users
        		JSONArray jsonArray = jsonPublicTimeline.getJSONArray("references");
        		// Add all fetched messages tp the database
        		for( int i=0; i < jsonArray.length(); i++ ) {
        			try {
        				JSONObject reference = jsonArray.getJSONObject(i);
        				// Check that we are looking at a user object
        				if ( !reference.getString("type").equals("user") ) {
        					continue;
        				}
        				//Log.d(TAG_YSERVICE, reference.getString("type") + ":" + reference.toString());
        				// TODO: Skip parsing after last "user" type
        				// Add the user reference to the database
	        			yammerData.addUser(reference);
        			} catch( Exception e ) {
        				if (G.DEBUG) Log.w(TAG_YSERVICE, e.getMessage());
	        		}
        		}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			

			try {
				if (G.DEBUG) Log.d(TAG_YSERVICE, "Updating public message timeline");
				// Retrieve all messages
        		JSONArray jsonArray = jsonPublicTimeline.getJSONArray("messages");
        		// Add all fetched messages tp the database
        		for( int i=0; i < jsonArray.length(); i++ ) {
        			// Add the message reference to the database
        			long messageId = yammerData.addMessage(getCurrentNetworkId(), jsonArray.getJSONObject(i));
        			// Is this my own message?
        			boolean ownMessage = yammerData.isMessageFromUser(messageId, getCurrentUserId());
        			// Only ask if notification is required if none of
        			// the previous messages had notification requirement
        			if ( notificationRequired == false ) {
        				notificationRequired = !ownMessage;
        				if (G.DEBUG) Log.d(TAG_YSERVICE, "Notification required: " + notificationRequired);
        			}
        			// Only increment message counter if this is not one of our own messages
        			if ( !ownMessage ) {
        				// If we reach this point, a new message has been received - increment new message counter
        				newMessageCount ++;	        				
        			}
        			// If this is the first message in the list, we
        			// will use the ID to fetch messages newer_than
        			// the last message fetched
        			if ( i == 0 ) {
        				lastMessageId = messageId;
        				timelineUpdated = true;
        			}
        		}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			

		} catch (Exception e) {
			if (G.DEBUG) Log.e(TAG_YSERVICE, "An error occured while parsing JSON: " + e.getStackTrace());
			return;
		} finally {
			// Release the semaphore
			jsonUpdateSemaphore.release();
		}
		
		if ( timelineUpdated ) {
			// Is notification required?
			if ( notificationRequired ) {
				// Yep, so notify the user with a notification icon
				notifyUser(getResources().getString(R.string.new_yammer_message), NOTIFICATION_NEW_MESSAGE);				
			}
			// Store the last message ID in the database
			yammerData.updateLastMessageId(getCurrentNetworkId(), lastMessageId);
			// Send an intent
			Intent intent = new Intent( "com.yammer:PUBLIC_TIMELINE_UPDATED" );
			sendBroadcast(intent);
		}
	}

  private String getURLBase() {
    return YammerSettings.getUrl(getApplicationContext());
  }
	
	@Override
	public void onDestroy() {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::onDestroy");
		super.onDestroy();
		// It seems we were destroyed for some reason, so set the state to raw
		YammerService.CLIENT_STATE = STATE_RAW;
		// onStart will now trigger a reauthorization
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::onBind");
		this.bound = true;
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		if (G.DEBUG) Log.d(TAG_YSERVICE, "YammerService::onUnbind");
		this.bound = false;
		// Don't invoke onRebind, so return false
		return false;
	}

	
	private NWOAuth oAuth;
	
	private void resetOAuth() {
	  this.oAuth = null;
	}
	private NWOAuth getOAuth() {
	  if (null == this.oAuth) {
	    this.oAuth = new NWOAuth(getURLBase());
	  }
	  return this.oAuth;
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
	
}
