package com.yammer.v1;

import static android.provider.BaseColumns._ID;

import com.yammer.v1.models.Message;
import com.yammer.v1.models.Network;
import com.yammer.v1.models.URL;
import com.yammer.v1.models.User;
import com.yammer.v1.settings.SettingsActivity;
import com.yammer.v1.settings.SettingsEditor;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnKeyListener;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class YammerActivity extends Activity {

  //TODO: Move to YammerService
  public static final String INTENT_TIMELINE_INITIALIZE = "com.yammer.v1:TIMELINE_INITIALIZE";
  public static final String INTENT_MUST_AUTHENTICATE_DIALOG = "com.yammer.v1:MUST_AUTHENTICATE_DIALOG";
  
  public static final String INTENT_PUBLIC_TIMELINE_UPDATED = "com.yammer.v1:PUBLIC_TIMELINE_UPDATED";
  public static final String EXTRA_LAST_SEEN_MESSAGE_ID = "last_seen_message_id";
  
  public static final String INTENT_NETWORK_ERROR_FATAL = "com.yammer.v1:NETWORK_ERROR_FATAL";
  public static final String INTENT_NETWORK_ERROR_MINOR = "com.yammer.v1:NETWORK_ERROR_MINOR";
  public static final String INTENT_AUTHORIZATION_DONE = "com.yammer.v1:AUTHORIZATION_DONE";
  public static final String INTENT_AUTHORIZATION_START = "com.yammer.v1:AUTHORIZATION_START";

  private static final boolean DEBUG = G.DEBUG;
  
  private static final long TEMP_SLEEP = 500;

  private YammerService mYammerService = null;
  private SQLiteDatabase db = null;;
  private YammerIntentReceiver yammerIntentReceiver = null;
  boolean listViewInitialized = false;
  private static final String[] PROJECTION = new String[] {Message.FIELD_MESSAGE};
  // Whenever something starts to load, this will be increased 
  // by 1 - when loading stops, the counter is decreased and 
  // if it reaches zero, the loading animation disappears.
  private static int loadingRefCounter = 0;
  private final Semaphore loadingRefCounterSemaphore = new Semaphore(1);

  private void bindYammerService() {
    if(DEBUG) Log.d(getClass().getName(), "Binding ServiceConnection");
    if(null == mYammerService) {
      bindService(new Intent(YammerActivity.this, YammerService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
  }
  
  private void unbindYammerService() {
    if(DEBUG) Log.d(getClass().getName(), "Unbinding ServiceConnection");
    try {
      unbindService(mConnection);
    } catch(IllegalArgumentException ex) {
      ex.printStackTrace();
    }
  }

  private YammerService getYammerService() {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.getYammerService()");
    return mYammerService;
  }
  
  private void registerIntents() {
    if (DEBUG) Log.d(getClass().getName(), "Registering intents for Yammer");
    IntentFilter filter = new IntentFilter();
    filter.addAction(INTENT_PUBLIC_TIMELINE_UPDATED);
    filter.addAction(INTENT_TIMELINE_INITIALIZE);
    filter.addAction(INTENT_AUTHORIZATION_START);
    filter.addAction(INTENT_AUTHORIZATION_DONE);
    filter.addAction(INTENT_MUST_AUTHENTICATE_DIALOG);
    filter.addAction(INTENT_NETWORK_ERROR_MINOR);
    filter.addAction(INTENT_NETWORK_ERROR_FATAL);
    filter.addAction(YammerService.INTENT_CHANGE_NETWORK);
    filter.addAction(YammerService.INTENT_CHANGE_FEED);
    
    yammerIntentReceiver = new YammerIntentReceiver();        
    registerReceiver(yammerIntentReceiver, filter);
  }

  class YammerIntentReceiver extends BroadcastReceiver {

    public YammerIntentReceiver() {
    }
    
    public void onReceive(Context context, Intent intent) {
      /**
       * Launch the browser and let the user authenticate himself
       * and activate the application.
       */
      if (DEBUG) Log.d(getClass().getName(), "Intent received: " + intent.getAction());
      if ( INTENT_PUBLIC_TIMELINE_UPDATED.equals(intent.getAction()) ) {
        // Only allow updating the listview if it has been initialized
        if ( listViewInitialized ) {
          updateListView();
        }
      } else if ( INTENT_TIMELINE_INITIALIZE.equals(intent.getAction()) ) {				
        if (DEBUG) Log.d(getClass().getName(), "Initializing timeline");

        final TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
        db = getYammerService().getYammerData().getReadableDatabase();
        if (DEBUG) Log.d(getClass().getName(), "Querying for known messages in network");

        // remove logic here looking for users you are following or your id since call to /messages/following will already do this        

        String sql = 
          "select messages._id, messages.message, messages.message_id, messages.timestamp, users.mugshot_url, users.mugshot_md5, users.full_name, users.is_following, users.email, u1.full_name as replyee_full_name, u1.email as replyee_email from messages " + 
          "left join users on users.user_id=messages.user_id " +
          "left join messages as m1 on messages.replied_to_id=m1.message_id " + 
          "left join users as u1 on u1.user_id=m1.user_id where messages.deleted='0' AND messages.network_id=? order by messages.message_id desc";
        Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(getYammerService().getCurrentNetworkId())});
        cursor.moveToFirst();

        if (DEBUG) Log.d(getClass().getName(), "Creating new TweetListAdapter");
        TweetListAdapter tweetListAdapter = new TweetListAdapter(YammerActivity.this, R.layout.list_row, cursor, PROJECTION, new int[] {R.id.message} );
        if (DEBUG) Log.d(getClass().getName(), "Binding adapter to list: " + tweetListView);
        tweetListView.setAdapter(tweetListAdapter);
        startManagingCursor(cursor);                	

        // Register tweetlistview for context menu clicks
        if (DEBUG) Log.d(getClass().getName(), "Registering tweet list view to receive context menu events");
        registerForContextMenu(tweetListView);
        // Attach an onclick listener to the listview
        tweetListView.setOnItemClickListener( new OnItemClickListener() {
          public void onItemClick(AdapterView<?> adapterView, View view, int id, long row) {
            
            if(getSettings().isMessageClickReply()) {
              if (DEBUG) Log.d(getClass().getName(), "Replying to message");
              Message message = getYammerService().getYammerData().getMessage(row);
              if(null == message) {
                YammerActivity.this.toastUser(R.string.no_message_selected);
              } else {
                Intent intent = new Intent(YammerActivity.this, YammerReplyActivity.class);
                intent.putExtra("messageId", message.messageId);
                startActivityForResult(intent, YAMMER_REPLY_CREATE);
              }
              
            } else if(getSettings().isMessageClickMenu()) {
              if(null != view.getParent()) {
                openContextMenu(view);
              }
              
            } else {
              if (DEBUG) Log.d(getClass().getName(), "Viewing message");    							
              // Create activity YammerSettings
              Intent i = new Intent(YammerActivity.this, YammerMessage.class);
              // We use startActivityForResult because we want to know when
              // the authorization has completed. If startActivity is used,
              // no result can be delivered back - it is fire and forget.
              startActivityForResult(i, 1);
            }
          }                	
        });

        showHeaderForFeed(getSettings().getFeed());
        tweetListView.setSelector(R.layout.list_row_selector);
        tweetListView.setDividerHeight(1);
        createLoaderWheelView();
        // Should loader wheel display (i.e. a loading operation is in progress)
        displayLoaderWheel();                
        if (DEBUG) Log.d(getClass().getName(), "ListViewInitialized");
        listViewInitialized = true;
      } else if ( INTENT_MUST_AUTHENTICATE_DIALOG.equals(intent.getAction()) ) {
        try {
          removeDialog(ID_DIALOG_LOADING);
        } catch (Exception e) {
          e.printStackTrace();
        }
        showDialog(ID_DIALOG_MUST_AUTHENTICATE);
      } else if ( INTENT_AUTHORIZATION_START.equals(intent.getAction()) ) {
        // Show progress dialog
        showDialog(ID_DIALOG_LOADING);
      } else if ( INTENT_AUTHORIZATION_DONE.equals(intent.getAction()) ) {
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  // A delay in binding to the service may occur,
                  // so wait until bound
                  while (getYammerService() == null);
                  // Delete it from the server
                  showLoadingAnimation(true);
                  // Update the messages timeline
//                  getYammerService().updateCurrentUserData();
                  getYammerService().getMessages(false);
                  // Initialize the tweets view
                  sendBroadcast(INTENT_TIMELINE_INITIALIZE);
                  runOnUiThread( new Runnable() {
                    public void run() {
                      updateListView();        		    				    				
                    }
                  });
                } finally {
                  //if ( DEBUG ) Log.d(getClass().getName(), "REMOVE DIALOG LOADING");									
                  showLoadingAnimation(false);
                }
              }
            }).start();
      } else if (YammerService.INTENT_CHANGE_NETWORK.equals(intent.getAction()) ) {
        
      } else if (YammerService.INTENT_CHANGE_FEED.equals(intent.getAction()) ) {
        clearMessages();
        showHeaderForFeed(intent.getStringExtra(YammerService.EXTRA_FEED_NAME));
        
      } else if ( INTENT_NETWORK_ERROR_MINOR.equals(intent.getAction()) ) {
        /**
         * A minor error occurred (connection lost or similar - can be retried later)
         * no need to notify the user.
         */
      } else if ( INTENT_NETWORK_ERROR_FATAL.equals(intent.getAction()) ) {
        try {
          dismissDialog(ID_DIALOG_LOADING);
        } catch (Exception e) {
          // 
        }
        /**
         * A fatal network error has occured - user must be notified
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(YammerActivity.this);
        AlertDialog alertDialog = builder.create();
        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialog.setTitle("Error!");
        alertDialog.setMessage("A network error occured! Try again later.");
        alertDialog.setOnDismissListener(new OnDismissListener() {
          public void onDismiss(DialogInterface arg0) {
            updateAuthenticationUI();					
          }
        });
        alertDialog.show();
      }
    }
  };

  // Menu ID's
  private static final int MENU_REPLY = Menu.FIRST + 1;
  private static final int MENU_DELETE = Menu.FIRST + 2;
  private static final int MENU_FOLLOW = Menu.FIRST + 3;
  private static final int MENU_RELOAD = Menu.FIRST + 4;
  private static final int MENU_SETTINGS = Menu.FIRST + 5;
  private static final int MENU_FEEDS = Menu.FIRST + 6;
  private static final int MENU_VIEW_MESSAGE = Menu.FIRST + 7;
  private static final int MENU_VIEW_THREAD = Menu.FIRST + 8;
  private static final int MENU_UNFOLLOW = Menu.FIRST + 9;
  private static final int MENU_NETWORKS = Menu.FIRST + 10;
  private static final int MENU_URL = Menu.FIRST + 1000;

  // ??
  static final int YAMMER_REPLY_CREATE = 0;
  static final int YAMMER_SETTINGS_CREATE = 1;
  static final int YAMMER_ABOUT_CREATE = 2;

  // Dialog ID's
  private static final int ID_DIALOG_MUST_AUTHENTICATE = 0;
  private static final int ID_DIALOG_LOADING = 1;
  private static final int ID_DIALOG_ERROR_FATAL = 2;
  private static final int ID_DIALOG_FEEDS = 3;
  private static final int ID_DIALOG_NETWORKS = 4;
  
  private static View cVire = null;

  protected void displayLoaderWheel() {
    // Find the loader wheel image
    View view = (ImageView)findViewById(R.id.loader_animation_overlay);
    if ( view == null ) return;
    // Is something loading?
    if ( loadingRefCounter > 0 ) {
      if (DEBUG) Log.d(getClass().getName(), "Showing loader wheel: " + view);
      // Something must be loading, so show the view
      view.setVisibility(View.VISIBLE);
    } else {
      Log.d(getClass().getName(), "view.isShown: " + view.isShown());
      if ( view.isShown() ) {
        if (DEBUG) Log.d(getClass().getName(), "Hiding loader wheel: " + view);
        // Nothing is loading, so remove the view
        view.setVisibility(View.INVISIBLE);				
      } else {
        if (DEBUG) Log.d(getClass().getName(), "Hiding new loader wheel (thread transit): " + cVire);
        if (cVire != null) cVire.setVisibility(View.INVISIBLE);				
      }
    }		
  }

  protected void showLoadingAnimation(final boolean enabled) {
    runOnUiThread( new Runnable() {
      public void run() {
        try {
          // Acquire mutex
          try {
            loadingRefCounterSemaphore.acquire();
          } catch (InterruptedException e) {
            // It seams the thread calling this was interrupted, so just return
            return;
          }

          if ( enabled == true ) {
            loadingRefCounter ++;
          } else {
            loadingRefCounter --;
          }
          if (DEBUG) Log.d(getClass().getName(), "loadingRefCounter: " + loadingRefCounter);
          displayLoaderWheel();
        } finally {
          // Release mutex
          loadingRefCounterSemaphore.release();
        }
      }
    });
  }

  protected View createLoaderWheelView() {
    if (DEBUG) Log.d(getClass().getName(), "createLoaderWheelView");
    View loaderWheelView = (ImageView)findViewById(R.id.loader_animation_overlay); 
    if ( loaderWheelView == null /*Loader wheel view not shown yet*/ ) {
      if (DEBUG) Log.d(getClass().getName(), "loaderWheelView doesn't exist, so creating it.");
      // Get root view
      LayoutInflater factory = LayoutInflater.from(this);
      View v = factory.inflate(R.layout.loader_animation, null);
      //LinearLayout root = (LinearLayout) findViewById(R.id.root_layout);
      RelativeLayout.LayoutParams layoutParams = 
        new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
      // Start animating the view
      addContentView(v, layoutParams);
      // Start the animation
      loaderWheelView = (ImageView)findViewById(R.id.loader_animation_overlay);
      loaderWheelView.setBackgroundResource(R.anim.loader_wheel);
      AnimationDrawable frameAnimation = (AnimationDrawable) loaderWheelView.getBackground();
      frameAnimation.start();
    }
    cVire = loaderWheelView;
    return loaderWheelView;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    if (DEBUG) Log.d(getClass().getName(), ".onCreateDialog("+id+")");
    if ( id == ID_DIALOG_MUST_AUTHENTICATE ) {
      // Show "Start Yammer Authentication" dialog
      AuthenticateDialog authDialog = new AuthenticateDialog(this);
      authDialog.setCancelable(false);
      authDialog.setOnDismissListener(new OnDismissListener() {
        public void onDismiss(DialogInterface _intf) {
          sendBroadcast(YammerService.INTENT_AUTHENTICATION_COMPLETE);
        }
      });
      return authDialog;
    } else if ( id == ID_DIALOG_LOADING ) {
      // Show the progress dialog
      ProgressDialog progressDialog = new ProgressDialog(this);
      progressDialog.setMessage(getResources().getString(R.string.loading));
      progressDialog.setCancelable(true);
      progressDialog.setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface arg0) {
          // Cancel authorization
          if ( getYammerService() != null ) {
            // Show authentication dialog if necessary
            updateAuthenticationUI();
            // Notify service that account should be reset
            // Don't reset if already authorized
            if ( !YammerService.isAuthorized() ) {
              // Clear all account information
              sendBroadcast(YammerService.INTENT_RESET_ACCOUNT);
            }
          }
        }
      });
      return progressDialog;
    } else if ( id == ID_DIALOG_ERROR_FATAL ) {
      // 
    } else if ( id == ID_DIALOG_FEEDS ) {
      return createFeedDialog();		
    } else if ( id == ID_DIALOG_NETWORKS ) {
      return createNetworkDialog();    
    }
    
    return super.onCreateDialog(id);
  }

  private Dialog createFeedDialog() {
    final String[] feeds = new YammerData(this).getFeedNames(getSettings().getCurrentNetworkId());
    int selected = Arrays.asList(feeds).indexOf(getSettings().getFeed());
    if (selected < 0) selected = 0;
    
    return new AlertDialog.Builder(YammerActivity.this)
      .setTitle(R.string.select_feed)
      .setIcon(R.drawable.yammer_logo_medium)
      .setOnCancelListener(
          new OnCancelListener() {
            public void onCancel(DialogInterface _dialog) {
              YammerActivity.this.removeDialog(ID_DIALOG_FEEDS);
            }
          }
      ).setSingleChoiceItems(feeds, selected,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface _dialog, int _button) {
            YammerActivity.this.removeDialog(ID_DIALOG_FEEDS);
            String feed = feeds[_button];
            
            Intent intent = new Intent(YammerService.INTENT_CHANGE_FEED);
            intent.putExtra(YammerService.EXTRA_FEED_NAME, feed);
            sendBroadcast(intent);
          }
        }
    ).create();
  }

  private Dialog createNetworkDialog() {
    final Network[] networks = new YammerData(this).getNetworks();
    
    String[] names = new String[networks.length];
    long defaultNetworkId = getSettings().getCurrentNetworkId(); 
    int selected = 0;
    for(int ii=0; ii<names.length ;ii++) {
      names[ii] = networks[ii].name;
      if(defaultNetworkId == networks[ii].networkId) {
        selected = ii;
      }
    }
    
    return new AlertDialog.Builder(YammerActivity.this)
      .setTitle(R.string.select_network)
      .setIcon(R.drawable.yammer_logo_medium)
      .setOnCancelListener(
          new OnCancelListener() {
            public void onCancel(DialogInterface _dialog) {
              YammerActivity.this.removeDialog(ID_DIALOG_NETWORKS);
              YammerActivity.this.removeDialog(ID_DIALOG_FEEDS);
            }
          }
      ).setSingleChoiceItems(names, selected,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface _dialog, int _button) {
              YammerActivity.this.removeDialog(ID_DIALOG_NETWORKS);
              YammerActivity.this.removeDialog(ID_DIALOG_FEEDS);
              Network network = networks[_button];
              
              Intent intent = new Intent(YammerService.INTENT_CHANGE_NETWORK);
              intent.putExtra(YammerService.EXTRA_NETWORK_ID, network.networkId);
              sendBroadcast(intent);
            }
          }
        ).create();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_NETWORKS, Menu.NONE, R.string.menu_networks).setIcon(android.R.drawable.ic_menu_compass);
    menu.add(0, MENU_FEEDS, Menu.NONE, R.string.menu_feeds).setIcon(R.drawable.menu_feeds);
//    menu.add(0, MENU_DIRECTORY, Menu.NONE, R.string.menu_directory).setIcon(R.drawable.menu_directory);
    menu.add(0, MENU_RELOAD, Menu.NONE, R.string.menu_reload).setIcon(R.drawable.menu_refresh);
    menu.add(0, MENU_SETTINGS, Menu.NONE, R.string.menu_settings).setIcon(android.R.drawable.ic_menu_preferences);
    return (super.onCreateOptionsMenu(menu));
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu _menu) {
    YammerData yd = new YammerData(this);
    
    _menu.findItem(MENU_NETWORKS).setEnabled(0 < yd.getNetworks().length);
    _menu.findItem(MENU_FEEDS).setEnabled(0 < yd.getFeedNames(getSettings().getCurrentNetworkId()).length);
    
    return true;
  }

  @Override 
  public boolean onOptionsItemSelected(MenuItem item) {
    switch ( item.getItemId() ) {
    case MENU_RELOAD:
      if (DEBUG) Log.d(getClass().getName(), "MENU_RELOAD selected");
      reload();
      break;
    case MENU_SETTINGS:
      if (DEBUG) Log.d(getClass().getName(), "MENU_SETTINGS selected");
      startActivityForResult(new Intent(this, SettingsActivity.class), YAMMER_SETTINGS_CREATE);        
      break;
    case MENU_FEEDS:
      if (DEBUG) Log.d(getClass().getName(), "MENU_FEEDS selected");
      showDialog(ID_DIALOG_FEEDS);
      break;
    case MENU_NETWORKS:
      if (DEBUG) Log.d(getClass().getName(), "MENU_NETWORDS selected");
      showDialog(ID_DIALOG_NETWORKS);
      break;
    };
    return (super.onOptionsItemSelected(item));  
  }

  public void clearMessages() {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      public void run() {
        TweetListView view = (TweetListView) findViewById(R.id.tweet_list);
        if(null != view) {
          TweetListAdapter adapter = (TweetListAdapter)view.getAdapter();
          if(null != adapter ) {
            adapter.notifyDataSetInvalidated();
          }
        }
      }
    });
  }

  public void updateListView() {
    if (DEBUG) Log.d(getClass().getName(), ".updateListView");
    try {
      // Reconfigure the list view
      TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
      TweetListAdapter tweetListAdapter = (TweetListAdapter)tweetListView.getAdapter();
      SQLiteCursor cursor = (SQLiteCursor)tweetListAdapter.getCursor();
      cursor.setSelectionArguments(new String[] {String.valueOf(getYammerService().getCurrentNetworkId())});
      cursor.requery();
      tweetListAdapter.notifyDataSetChanged();
      
      // If we are at the top of the screen, then show the newest item
      if ( tweetListView.getFirstVisiblePosition() == 0 ) {
        if (DEBUG) Log.d(getClass().getName(), "Scrolling view to top");
        // Scroll to top
        tweetListView.setSelectionFromTop(0, 0);
      } else {
        // If looking at messages further down, show a notification                	
        //if (DEBUG) Log.d(getClass().getName(), "Notifying user of new message");
        //Toast.makeText(Yammer.this, "New message", Toast.LENGTH_LONG).show();
      }
    } catch (Exception e) {
      // Caught the cursor at the wrong time
      e.printStackTrace();
      if (DEBUG) Log.d(getClass().getName(), "Whoops.. Cursor or view wasn't valid. Makes no sense to continue.");        	
      return;        	
    }
    
  }

  private void reload() {
    new Thread(
        new Runnable() {
          public void run() {
            try {
              showLoadingAnimation(true);
              getYammerService().updateCurrentUserData();
              clearMessages();
              getYammerService().clearMessages();
              getYammerService().getMessages(true);
            } finally {
              showLoadingAnimation(false);									
            }
          }
        }).start();
  }

  private void updateMessages() {
    new Thread(
        new Runnable() {
          public void run() {
            try {
              showLoadingAnimation(true);
              getYammerService().getMessages(true);
            } finally {
              showLoadingAnimation(false);
            }
          }
        }).start();
  }


  public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
    if (DEBUG) Log.d(getClass().getName(), "Create context menu");

    AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
    
    // TODO: Refactor this into 
    // message = YammerData().getMessage(rowinfo.id); 
    // YammerData().getUser(message.userId);
    String sql = "select messages._id, messages.message, messages.message_id, messages.user_id, users.full_name, users.is_following, urls.url from messages join users on messages.user_id=users.user_id left join urls on messages.message_id=urls.message_id where messages." + _ID + "=" + info.id;
    SQLiteDatabase db = getYammerService().getYammerData().getReadableDatabase();
    Cursor c = null;
    try { 
      c = db.rawQuery(sql, null);
      c.moveToFirst();
      
      if (DEBUG) Log.d(getClass().getName(), "c.getCount(): " + c.getCount());
      if(0 == c.getCount()) {
        toastUser(R.string.no_message_selected);
        return;
      }
    
      long userId = c.getLong(c.getColumnIndex(User.FIELD_USER_ID));
      String message = c.getString(c.getColumnIndex(Message.FIELD_MESSAGE));
      //menu.setHeaderTitle(R.string.popup_title_label);
      // Is this my own message?
      boolean myself = false;
      //TODO: don't call getYammerService().getCurrentUserId()
      if ( userId == getYammerService().getCurrentUserId() ) {
        myself = true;
      }
      // Start building the menu
      menu.setHeaderTitle(message);   
      menu.setHeaderIcon(R.drawable.yammer_logo_small);
      // For version 1.1
      //menu.add(0, MENU_VIEW_MESSAGE, ContextMenu.NONE, R.string.view_message_label);
      // For version 1.2
      //menu.add(0, MENU_VIEW_THREAD, ContextMenu.NONE, R.string.view_thread_label);
      menu.add(0, MENU_REPLY, ContextMenu.NONE, R.string.reply_label);
      // I don't want to be able to follow myself
      if ( !myself ) {
        String fullName = c.getString(c.getColumnIndex(User.FIELD_FULL_NAME));
        if ( 0 == c.getInt(c.getColumnIndex(User.FIELD_IS_FOLLOWING)) ) {
          menu.add(0, MENU_FOLLOW, ContextMenu.NONE, getResources().getString(R.string.follow_label) + " " + fullName);
        } else {	            
          menu.add(0, MENU_UNFOLLOW, ContextMenu.NONE, getResources().getString(R.string.unfollow_label) + " " + fullName);
        }
      } else {
        menu.add(0, MENU_DELETE, ContextMenu.NONE, R.string.delete_label).setIcon(R.drawable.yammer_logo_small);
      }
  
      // Submenu for URLs
      //Menu urlSubMenu = null;
      // Add any URL's that may have been enclosed in the message
      int columnIndex = c.getColumnIndex(URL.FIELD_URL);
      for ( int i=0; i<c.getCount(); i++) {
        String url = c.getString(columnIndex);
        // No URLs in this post, so break	
        if ( url == null ) {
          break;
        }
        // Add submenu on first URL fetched
        //if ( i == 0 ) {
        //	urlSubMenu = menu.addSubMenu("URLs");            	
        //}
        if (DEBUG) Log.d(getClass().getName(), "URL: " + url);
        Intent browserLaunchUrlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        menu.add(0, MENU_URL, ContextMenu.NONE, getResources().getString(R.string.url_icon)+url)
        .setIntent(browserLaunchUrlIntent);        	
        c.moveToNext();
      }
      
    } finally {
      if(null != c) {
        c.close();
      }
    }
  }

  public boolean onContextItemSelected(MenuItem item) {		
    String errorMessage = "";

    try {
      AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
      // If URL is selected, info will be null
      if ( info == null ) {
        return false;
      }
      
      // Get the row ID for the item clicked
      long rowId = info.id;
      String sql = "select _id, user_id, message_id from messages where " + _ID + "=" + rowId;
      SQLiteDatabase db = getYammerService().getYammerData().getReadableDatabase();
      Cursor c = null;
      try {
        c = db.rawQuery(sql, null);
        c.moveToFirst();	
        // Retrieve the message ID og the message clicked on
        int columnIndex = c.getColumnIndex(Message.FIELD_MESSAGE_ID);
        if (DEBUG) Log.d(getClass().getName(), "columnIndex: " + columnIndex);
        final long messageId = c.getLong(columnIndex);
        columnIndex = c.getColumnIndex(User.FIELD_USER_ID);
        final long userId = c.getLong(columnIndex);
      
        // Which item was selected
        switch ( item.getItemId() ) {
  
        case MENU_VIEW_MESSAGE:
          if (DEBUG) Log.d(getClass().getName(), "MENU_VIEW_MESSAGE selected");
          break;
        case MENU_VIEW_THREAD:
          if (DEBUG) Log.d(getClass().getName(), "MENU_VIEW_THREAD selected");				
          break;
        case MENU_REPLY:
          if (DEBUG) Log.d(getClass().getName(), "MENU_REPLY selected");				
          // Start the reply activity
          Intent i = new Intent(this, YammerReplyActivity.class);
          i.putExtra("messageId", messageId);
          startActivityForResult(i, YAMMER_REPLY_CREATE);        
          break;
        case MENU_DELETE:
          if (DEBUG) Log.d(getClass().getName(), "MENU_DELETE selected");
          // Delete the item from the database
          // Send delete request to the database
          new Thread(
              new Runnable() {
                public void run() {
                  if (DEBUG) Log.d(getClass().getName(), "Deleting message with ID " + messageId);
                  try {
                    showLoadingAnimation(true);
                    getYammerService().deleteMessage(messageId);
                  } catch (YammerProxy.YammerProxyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                  } finally {
                    showLoadingAnimation(false);									
                  }
                  if (DEBUG) Log.d(getClass().getName(), "Message with ID " + messageId + " deleted!");
                }
              }).start();
          break;
        case MENU_FOLLOW:
          if (DEBUG) Log.d(getClass().getName(), "MENU_FOLLOW selected");
          new Thread(
              new Runnable() {
                public void run() {
                  // Delete it from the server
                  if (DEBUG) Log.d(getClass().getName(), "Following user with ID " + userId);
                  try {
                    showLoadingAnimation(true);
                    getYammerService().followUser(userId);
                  } catch (YammerProxy.YammerProxyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                  } finally {
                    showLoadingAnimation(false);									
                  }
                  if (DEBUG) Log.d(getClass().getName(), "User with ID " + userId + " followed!");
                }
              }).start();
          break;
        case MENU_UNFOLLOW:
          if (DEBUG) Log.d(getClass().getName(), "MENU_UNFOLLOW selected");
          new Thread(
              new Runnable() {
                public void run() {
                  // Delete it from the server
                  if (DEBUG) Log.d(getClass().getName(), "Unfollowing user with ID " + userId);
                  try {
                    showLoadingAnimation(true);
                    getYammerService().unfollowUser(userId);
                  } catch (YammerProxy.YammerProxyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                  } finally {
                    showLoadingAnimation(false);									
                  }
                  if (DEBUG) Log.d(getClass().getName(), "User with ID " + userId + " unfollowed!");
                }
              }).start();
          break;
        case MENU_URL:
          Intent intent = item.getIntent();
          this.startActivity(intent);
          break;
        }
        
      } finally {
        if(null != c) {
          c.close();
        } 
      }
      
      // Return true to consume
      return true;

    } catch (CursorIndexOutOfBoundsException e) {
      errorMessage = "Could not select message. Please reload and try again.";
    } catch (Exception e) {
      errorMessage = "An unknown error occured, please try again.";
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(YammerActivity.this);
    AlertDialog alertDialog = builder.create();
    alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
    alertDialog.setTitle("Error!");
    alertDialog.setMessage(errorMessage);			
    alertDialog.show();
    return false;			
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onActivityResult");
    switch(requestCode) {
    case YAMMER_REPLY_CREATE:
      if ( resultCode == 0 ) {
        if (DEBUG) Log.d(getClass().getName(), "YAMMER_REPLY_CREATE");
        Bundle bundle = data.getExtras();
        final String reply = bundle.getString("reply");
        // Get the message ID we replied upon
        final long messageId = bundle.getLong("messageId");
        // Post the message to the network
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  getYammerService().postMessage(reply, messageId);
                  Thread.sleep(TEMP_SLEEP);                  
                  updateMessages();
                } catch (YammerProxy.YammerProxyException e) {
                  e.printStackTrace();
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
              }
            }).start();
      }
      break;
    case YAMMER_SETTINGS_CREATE:
      if (DEBUG) Log.d(getClass().getName(), "YAMMER_SETTINGS_CREATE: result = " + resultCode);
      if ( resultCode == 0 ) {
      }
      break;
    default:
      break;
    }
  }

  private void updateAuthenticationUI() {
    if(YammerService.isAuthorized()) {
      sendBroadcast(INTENT_TIMELINE_INITIALIZE);
    } else { 
      sendBroadcast(INTENT_MUST_AUTHENTICATE_DIALOG);
    }		
  }

  private ServiceConnection mConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      if (DEBUG) Log.d(getClass().getName(), "ServiceConnection.onServiceConnected");
      mYammerService = ((YammerService.YammerBinder)service).getService();
      if (mYammerService == null) return;
      updateAuthenticationUI();
    }

    public void onServiceDisconnected(ComponentName className) {
      if (DEBUG) Log.d(getClass().getName(), "ServiceConnection.onServiceDisconnected");
      mYammerService = null;
    }
  };

  public Object onRetainNonConfigurationInstance() {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onRetainNonConfigurationInstance");
    return super.onRetainNonConfigurationInstance();
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onCreate");
    super.onCreate(savedInstanceState);
    if (DEBUG) Log.d(getClass().getName(), "onCreate.savedInstance: " + savedInstanceState);

    //setTheme(android.R.style.Theme_Black_NoTitleBar);
    setContentView(R.layout.yammer_activity);

    registerIntents();

    // Setup a clicklistener for the message textedit
    final EditText tweetEditor = getEditor();
    tweetEditor.setOnKeyListener(new OnKeyListener() {
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if ( keyCode == KeyEvent.KEYCODE_ENTER  ) {
          // We only want to post the message, when key is released
          if ( event.getAction() == KeyEvent.ACTION_UP ) {
            final String message = getEditorText();
            if (DEBUG) Log.d(getClass().getName(), "POST MESSAGE: " + message);
            // Post the message to the network
            new Thread(
                new Runnable() {
                  public void run() {
                    try {
                      getYammerService().postMessage(message, 0);
                      Thread.sleep(TEMP_SLEEP);
                      updateMessages();
                    } catch (YammerProxy.YammerProxyException e) {
                      e.printStackTrace();
                    } catch (InterruptedException e) {
                      e.printStackTrace();
                    }
                  }
                }).start();
            
            setEditorText(null);
            
          }
          return true;
        }
        return false;
      }

    });

    // Set key listener used to display "what are you working on" overlay
    tweetEditor.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View _view, MotionEvent _event) {
        if (DEBUG) Log.d(getClass().getName(), "tweetEditor.onTouch");
        
        if(null == getEditorText()) {
          setEditorText("");
        }

        return false;
      }
    });

    setEditorText(null);
  }

  private EditText editor = null;
  private EditText getEditor() {
    if (null == editor) editor = (EditText)findViewById(R.id.tweet_editor);
    return editor; 
  }

  private TextView feedHeader = null;
  private TextView getFeedHeader() {
    if(null == feedHeader) feedHeader = (TextView)findViewById(R.id.feed_label);
    return feedHeader;
  }

  private TextView updatedAtHeader = null;
  private TextView getUpdatedAtHeader() {
    if(null == updatedAtHeader) updatedAtHeader = (TextView)findViewById(R.id.updated_at_label);
    return updatedAtHeader;
  }

  /**
   * Set editor text.
   * 
   * @param _text new value (null sets text to prompt)
   */
  private void setEditorText(String _text) {
    EditText editor = getEditor();

    final String prompt = getString(R.string.yam_prompt);
    if(null == _text) _text = prompt;

    editor.setText(_text);

    if(prompt.equals(_text)) {
      editor.setTextColor(0xFF888888);
    } else {
      editor.setTextColor(0xFF000000);
    }
  }

  private String getEditorText() {
    String text = getEditor().getText().toString();
    if(getString(R.string.yam_prompt).equals(text)) {
      text = null;
    }
    return text;
  }

  public void onStart() {
    super.onStart();
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onStart");
    bindYammerService();
  }

  public void onResume() {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onResume");
    super.onResume();
    
    sendBroadcast(YammerService.INTENT_DISABLE_NOTIFICATION);
    
    if (getYammerService() != null) {
      getYammerService().resetMessageCount();
      
      if(getSettings().updateOnResume() && getYammerService().isAuthorized()) {
        getYammerService().getMessages(true);
      }
      
    } else {
      if (DEBUG) Log.d(getClass().getName(), "mYammerService was null - could not do onResume tasks for YammerService");
    }
  }

  public void onPause() {
    super.onPause();
    sendBroadcast(YammerService.INTENT_ENABLE_NOTIFICATION);
  }
  
  public void onStop() {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onStop");
    super.onStop();
    // mYammerService may be null here if keyboard is opened, closed very fast
    if ( getYammerService() != null ) {
      // Reset the message count - we probably saw any new message
      getYammerService().resetMessageCount();
    }
    
    unbindYammerService();
    // TODO: Unregister receiver
    // Make sure intent receiver was registered before unregistering it
  }

  public void onDestroy() {
    if (DEBUG) Log.d(getClass().getName(), "Yammer.onDestroy");
    if ( yammerIntentReceiver != null ) {
      if (DEBUG) Log.d(getClass().getName(), "Unregistering receiver");
      unregisterReceiver(yammerIntentReceiver);
      yammerIntentReceiver = null;
    }     
    if ( isFinishing() ){
      if (DEBUG) Log.i(getClass().getName(), "Activity is finishing");
    }
    //Debug.stopMethodTracing();
    super.onDestroy();
  }

  public void showHeaderForFeed(String _feed) {
    if(YammerProxy.DEFAULT_FEED.equals(_feed)) {
      getFeedHeader().setVisibility(View.GONE);
      getEditor().setVisibility(View.VISIBLE);
    } else {
      getEditor().setVisibility(View.GONE);
      getFeedHeader().setVisibility(View.VISIBLE);
      getFeedHeader().setText(_feed+':');
    }

    getUpdatedAtHeader().setText(
        formatTimestamp(R.string.updated_at_header, getSettings().getUpdatedAt())
    );
  }

  private String formatTimestamp(int _res, Date _date) {
    return new SimpleDateFormat(getString(_res)).format(_date);
  }

  SettingsEditor settings;
  private SettingsEditor getSettings() {
    if(null == this.settings) {
      this.settings = new SettingsEditor(getApplicationContext());
    }
    return this.settings;
  }
  
  private void sendBroadcast(String _intent) {
    sendBroadcast(new Intent(_intent));
  }

  private void toastUser(final int _resId, final Object... _args) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      public void run() {
        Toast.makeText(getApplicationContext(), String.format(getText(_resId).toString(), _args), Toast.LENGTH_LONG).show();
      }
    });
  }

}
