package com.yammer;

import static android.provider.BaseColumns._ID;

import com.yammer.YammerDataConstants;

import java.util.Arrays;
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
import android.os.IBinder;
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
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class YammerActivity extends Activity {

  private static final boolean DEBUG = G.DEBUG;

  private static final String TAG_Y = "Yammer";

  private YammerService mYammerService = null;
  private SQLiteDatabase db = null;;
  private YammerIntentReceiver yammerIntentReceiver = null;
  boolean listViewInitialized = false;
  private static final String[] PROJECTION = new String[] {YammerDataConstants.MESSAGE};
  // Whenever something starts to load, this will be increased 
  // by 1 - when loading stops, the counter is decreased and 
  // if it reaches zero, the loading animation disappears.
  private static int loadingRefCounter = 0;
  private View noTextOverlayView = null; 
  private final Semaphore loadingRefCounterSemaphore = new Semaphore(1);

  private YammerService getYammerService() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::getYammerService()");
    if ( mYammerService == null ) {
      bindService( 	new Intent(YammerActivity.this, YammerService.class), 
          mConnection, 
          Context.BIND_AUTO_CREATE);    				
    }
    return mYammerService;
  }

  class YammerIntentReceiver extends BroadcastReceiver {
    public YammerIntentReceiver() {
    }

    /**
     * Intent receiver
     */
    public void onReceive(Context context, Intent intent) {
      /**
       * Launch the browser and let the user authenticate himself
       * and activate the application.
       */
      if (DEBUG) Log.d(TAG_Y, "Intent received: " + intent.getAction());
      if ( intent.getAction().equals("com.yammer:PUBLIC_TIMELINE_UPDATED") ) {
        // Only allow updating the listview if it has been initialized
        if ( listViewInitialized ) {
          updateListView();
        }
      } else if ( intent.getAction().equals("com.yammer:TIMELINE_INITIALIZE") ) {				
        if (DEBUG) Log.d(TAG_Y, "Initializing timeline");

        final TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
        db = getYammerService().yammerData.getReadableDatabase();
        if (DEBUG) Log.d(TAG_Y, "Querying for known messages in network");

        // remove logic here looking for users you are following or your id since call to /messages/following will already do this        

        String sql = 
          "select messages._id, messages.message, messages.message_id, messages.timestamp, users.mugshot_url, users.mugshot_md5, users.full_name, users.is_following, users.email, u1.full_name as replyee_full_name, u1.email as replyee_email from messages " + 
          "left join users on users.user_id=messages.user_id " +
          "left join messages as m1 on messages.replied_to_id=m1.message_id " + 
          "left join users as u1 on u1.user_id=m1.user_id where messages.deleted='0' AND messages.network_id='"+getYammerService().getCurrentNetworkId()+"' order by messages.message_id desc";
        Cursor cursor = db.rawQuery(sql, null);
        cursor.moveToFirst();

        if (DEBUG) Log.d(TAG_Y, "Creating new TweetListAdapter");
        TweetListAdapter tweetListAdapter = new TweetListAdapter(YammerActivity.this, R.layout.list_row, cursor, PROJECTION, new int[] {R.id.message} );
        if (DEBUG) Log.d(TAG_Y, "Binding adapter to list: " + tweetListView);
        tweetListView.setAdapter(tweetListAdapter);
        startManagingCursor(cursor);                	

        // Register tweetlistview for context menu clicks
        if (DEBUG) Log.d(TAG_Y, "Registering tweet list view to receive context menu events");
        registerForContextMenu(tweetListView);
        // Attach an onclick listener to the listview
        tweetListView.setOnItemClickListener( new OnItemClickListener() {
          public void onItemClick(AdapterView<?> adapterView, View view, int id, long row) {
            if ( YammerSettings.getMessageClickBehaviour(YammerActivity.this).equals("reply") ) {
              if (DEBUG) Log.d(TAG_Y, "Replying to message");
              long rowId = row;
              String sql = "select _id, message_id from messages where " + _ID + "=" + rowId;
              SQLiteDatabase db = getYammerService().yammerData.getReadableDatabase();
              Cursor c = db.rawQuery(sql, null);
              c.moveToFirst();
              // Just show the reply activity 
              Intent i = new Intent(YammerActivity.this, YammerReply.class);
              // Post the message ID being replied upon along with the intent
              int columnIndex = c.getColumnIndex(YammerDataConstants.MESSAGE_ID);
              if (DEBUG) Log.d(TAG_Y, "columnIndex: " + columnIndex);
              long messageId = c.getLong(columnIndex);
              i.putExtra("messageId", messageId);
              startActivityForResult(i, YAMMER_REPLY_CREATE);
              c.close();    							
            } else {
              if (DEBUG) Log.d(TAG_Y, "Viewing message");    							
              // Create activity YammerSettings
              Intent i = new Intent(YammerActivity.this, YammerMessage.class);
              // We use startActivityForResult because we want to know when
              // the authorization has completed. If startActivity is used,
              // no result can be delivered back - it is fire and forget.
              startActivityForResult(i, 1);
            }
          }                	
        });

        showHeaderForFeed(YammerSettings.getFeed(YammerActivity.this));
        tweetListView.setSelector(R.layout.list_row_selector);
        tweetListView.setDividerHeight(1);
        createLoaderWheelView();
        // Should loader wheel display (i.e. a loading operation is in progress)
        displayLoaderWheel();                
        if (DEBUG) Log.d(TAG_Y, "ListViewInitialized");
        listViewInitialized = true;
      } else if ( intent.getAction().equals("com.yammer:MUST_AUTHENTICATE_DIALOG") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer::MUST_AUTHENTICATE_DIALOG");
        try {
          removeDialog(ID_DIALOG_LOADING);
        } catch (Exception e) {
          e.printStackTrace();
        }
        showDialog(ID_DIALOG_MUST_AUTHENTICATE);
      } else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_INITIATE") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_INITIATE");
        // Hide the authenticate dialog
        removeDialog(ID_DIALOG_MUST_AUTHENTICATE);
        // Start the authorization
        getYammerService().initiateAuthorization();
      } else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_START") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_START");
        // Show progress dialog
        showDialog(ID_DIALOG_LOADING);
      } else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_DONE") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_DONE");
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
                  getYammerService().updatePublicMessages();
                  getYammerService().updateCurrentUserData();
                  // Initialize the tweets view
                  sendBroadcast("com.yammer:TIMELINE_INITIALIZE");
                  runOnUiThread( new Runnable() {
                    public void run() {
                      updateListView();        		    				    				
                    }
                  });
                } catch (YammerProxy.ConnectionProblem e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (YammerProxy.AccessDeniedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } finally {
                  //if ( DEBUG ) Log.d(TAG_Y, "REMOVE DIALOG LOADING");									
                  showLoadingAnimation(false);
                }
              }
            }).start();
      } else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_BROWSER") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_BROWSER");
        if ( getYammerService() != null && YammerService.isAuthenticating == true ) {
          if ( DEBUG ) Log.d(TAG_Y, "REMOVE DIALOG LOADING");
          removeDialog(ID_DIALOG_LOADING);
          if ( DEBUG ) Log.d(TAG_Y, "Starting browser");
          // Fetch responseUrl from intent extras
          Bundle bundle = intent.getExtras();
          String responseUrl = bundle.getString("responseUrl");
          // Create new intent to launch the browser
          Intent browserIntent = new Intent();
          browserIntent.setClassName("com.yammer", "com.yammer.Browser");
          // Put the reponseUrl into the intent
          browserIntent.putExtra("responseUrl", responseUrl);
          startActivityForResult(browserIntent, YAMMER_BROWSER_CREATE);
        } else {
          if ( DEBUG ) Log.d(TAG_Y, "Browser not starting - authentication not in progress");        			
        }
      } else if ( intent.getAction().equals("com.yammer:NETWORK_ERROR_MINOR") ) {
        /**
         * A minor error occurred (connection lost or similar - can be retried later)
         * no need to notify the user.
         */
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:NETWORK_ERROR_MINOR");
      } else if ( intent.getAction().equals("com.yammer:NETWORK_ERROR_FATAL") ) {
        if ( DEBUG ) Log.d(TAG_Y, "com.yammer:NETWORK_ERROR_FATAL");
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
  private static final int MENU_URL = Menu.FIRST + 1000;

  // ??
  static final int YAMMER_REPLY_CREATE = 0;
  static final int YAMMER_SETTINGS_CREATE = 1;
  static final int YAMMER_ABOUT_CREATE = 2;
  static final int YAMMER_BROWSER_CREATE = 3;

  // Dialog ID's
  private static final int ID_DIALOG_MUST_AUTHENTICATE = 0;
  private static final int ID_DIALOG_LOADING = 1;
  private static final int ID_DIALOG_ERROR_FATAL = 2;
  private static final int ID_DIALOG_FEEDS = 3;
  private static View cVire = null;

  protected void displayLoaderWheel() {
    // Find the loader wheel image
    View view = (ImageView)findViewById(R.id.loader_animation_overlay);
    if ( view == null ) return;
    // Is something loading?
    if ( loadingRefCounter > 0 ) {
      if (DEBUG) Log.d(TAG_Y, "Showing loader wheel: " + view);
      // Something must be loading, so show the view
      view.setVisibility(View.VISIBLE);
    } else {
      Log.d(TAG_Y, "view.isShown: " + view.isShown());
      if ( view.isShown() ) {
        if (DEBUG) Log.d(TAG_Y, "Hiding loader wheel: " + view);
        // Nothing is loading, so remove the view
        view.setVisibility(View.INVISIBLE);				
      } else {
        if (DEBUG) Log.d(TAG_Y, "Hiding new loader wheel (thread transit): " + cVire);
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
          if (DEBUG) Log.d(TAG_Y, "loadingRefCounter: " + loadingRefCounter);
          displayLoaderWheel();
        } finally {
          // Release mutex
          loadingRefCounterSemaphore.release();
        }
      }
    });
  }

  protected View createLoaderWheelView() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::createLoaderWheelView");
    View loaderWheelView = (ImageView)findViewById(R.id.loader_animation_overlay); 
    if ( loaderWheelView == null /*Loader wheel view not shown yet*/ ) {
      if (DEBUG) Log.d(TAG_Y, "loaderWheelView doesn't exist, so creating it.");
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
    if (DEBUG) Log.d(TAG_Y, "Yammer::onCreateDialog("+id+")");
    if ( id == ID_DIALOG_MUST_AUTHENTICATE ) {
      // Show "Start Yammer Authentication" dialog
      AuthenticateDialog authDialog = new AuthenticateDialog(YammerActivity.this);
      authDialog.setCancelable(true);
      authDialog.setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface arg0) {
          // if canceled, then finish this activity
          finish();
        }
      });
      // If canceled, then finish activity.
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
            getYammerService().cancelAuthorization();
            // Show authentication dialog if necessary
            updateAuthenticationUI();
            // Notify service that account should be reset
            // Don't reset if already authorized
            if ( !YammerService.isAuthorized() ) {
              // Clear all account information
              sendBroadcast("com.yammer:RESET_ACCOUNT");
            }
          }
        }
      });
      return progressDialog;
    } else if ( id == ID_DIALOG_ERROR_FATAL ) {
      // 
    } else if ( id == ID_DIALOG_FEEDS ) {
      return createFeedDialog();		
    }
    return super.onCreateDialog(id);
  }

  private Dialog createFeedDialog() {
    final YammerData yd = new YammerData(this);
    final String[] feeds = yd.getFeedNames();
    int selected = Arrays.asList(feeds).indexOf(YammerSettings.getFeed(this));
    if (selected < 0) selected = 0;

    return new AlertDialog.Builder(YammerActivity.this)
    .setTitle(R.string.select_feed)
    .setIcon(R.drawable.yammer_logo_medium)
    .setSingleChoiceItems(feeds, selected,
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface _dialog, int _button) {
        String feed = feeds[_button];
        if (DEBUG) Log.d(TAG_Y, "Feed '" + feed + "' selected" );                  
        YammerSettings.setFeed(YammerActivity.this, feed);
        showHeaderForFeed(feed);
        yd.clearMessages();
        reload();
        _dialog.dismiss();
      }
    }
    ).create();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_FEEDS, Menu.NONE, R.string.feeds).setIcon(R.drawable.menu_show_list);
    menu.add(0, MENU_RELOAD, Menu.NONE, R.string.reload).setIcon(R.drawable.menu_refresh);
    menu.add(0, MENU_SETTINGS, Menu.NONE, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
    return (super.onCreateOptionsMenu(menu));
  }

  public void updateListView() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::updateListView");
    try {
      // Reconfigure the list view
      TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
      TweetListAdapter tweetListAdapter = (TweetListAdapter)tweetListView.getAdapter();
      SQLiteCursor cursor = (SQLiteCursor)tweetListAdapter.getCursor();
      cursor.requery();
      tweetListAdapter.notifyDataSetChanged();
      // If we are at the top of the screen, then show the newest item
      if ( tweetListView.getFirstVisiblePosition() == 0 ) {
        if (DEBUG) Log.d(TAG_Y, "Scrolling view to top");
        // Scroll to top
        tweetListView.setSelectionFromTop(0, 0);
      } else {
        // If looking at messages further down, show a notification                	
        //if (DEBUG) Log.d(TAG_Y, "Notifying user of new message");
        //Toast.makeText(Yammer.this, "New message", Toast.LENGTH_LONG).show();
      }
    } catch (Exception e) {
      // Caught the cursor at the wrong time
      e.printStackTrace();
      if (DEBUG) Log.d(TAG_Y, "Whoops.. Cursor or view wasn't valid. Makes no sense to continue.");        	
      return;        	
    }
  }

  @Override 
  public boolean onOptionsItemSelected(MenuItem item) {
    // Which item was selected
    switch ( item.getItemId() ) {
    case MENU_RELOAD:
      if (DEBUG) Log.d(TAG_Y, "MENU_RELOAD selected");
      reload();
      break;
    case MENU_SETTINGS:
      if (DEBUG) Log.d(TAG_Y, "MENU_SETTINGS selected");
      startActivityForResult(new Intent(this, YammerSettings.class), YAMMER_SETTINGS_CREATE);        
      break;
    case MENU_FEEDS:
      if (DEBUG) Log.d(TAG_Y, "MENU_FEEDS selected");
      // Create activity YammerSettings
      showDialog(ID_DIALOG_FEEDS);
      break;
    };
    return (super.onOptionsItemSelected(item));  
  }

  private void reload() {
    new Thread(
        new Runnable() {
          public void run() {
            // Delete it from the server
            try {
              showLoadingAnimation(true);
              // Instruct YammerService activity to reload the timeline
              getYammerService().updatePublicMessages();
              getYammerService().updateCurrentUserData();
              // Update the timeline view
              sendBroadcast("com.yammer:PUBLIC_TIMELINE_UPDATED");
            } catch (YammerProxy.AccessDeniedException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            } catch (YammerProxy.ConnectionProblem e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            } finally {
              showLoadingAnimation(false);									
            }
          }
        }).start();
  }

  public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
    if (DEBUG) Log.d(TAG_Y, "Create context menu");
    // Get the row ID
    AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
    long rowId = info.id;
    // Select
    String sql = "select messages._id, messages.message, messages.message_id, messages.user_id, users.full_name, users.is_following, urls.url from messages join users on messages.user_id=users.user_id left join urls on messages.message_id=urls.message_id where messages." + _ID + "=" + rowId;
    SQLiteDatabase db = getYammerService().yammerData.getReadableDatabase();
    Cursor c = db.rawQuery(sql, null);
    c.moveToFirst();
    // Get the user ID of the user who  posted the message
    int columnIndex = c.getColumnIndex(YammerDataConstants.USER_ID);
    long userId = c.getLong(columnIndex);
    // Get the message posted
    columnIndex = c.getColumnIndex(YammerDataConstants.MESSAGE);
    String message = c.getString(columnIndex);
    // Get the full name of the user who posted the message
    columnIndex = c.getColumnIndex(YammerDataConstants.FULL_NAME);
    String fullName = c.getString(columnIndex);
    // Get the full name of the user who posted the message
    columnIndex = c.getColumnIndex(YammerDataConstants.IS_FOLLOWING);
    int isFollowing = c.getInt(columnIndex);
    //menu.setHeaderTitle(R.string.popup_title_label);
    // Is this my own message?
    boolean myself = false;
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
      if ( isFollowing == 0 ) {
        // Not following already, so allow follow
        menu.add(0, MENU_FOLLOW, ContextMenu.NONE, getResources().getString(R.string.follow_label) + " " + fullName);
      } else {	            
        // Do I already follow this user ID? Then allow unfollow
        menu.add(0, MENU_UNFOLLOW, ContextMenu.NONE, getResources().getString(R.string.unfollow_label) + " " + fullName);
      }
    }
    // If this is myself, then there is a delete button
    if ( myself ) {
      menu.add(0, MENU_DELETE, ContextMenu.NONE, R.string.delete_label).setIcon(R.drawable.yammer_logo_small);
    }

    // Submenu for URLs
    //Menu urlSubMenu = null;
    if (DEBUG) Log.d(TAG_Y, "c.getCount(): " + c.getCount());
    // Add any URL's that may have been enclosed in the message
    columnIndex = c.getColumnIndex(YammerDataConstants.FIELD_URLS_URL);
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
      if (DEBUG) Log.d(TAG_Y, "URL: " + url);
      Intent browserLaunchUrlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      menu.add(0, MENU_URL, ContextMenu.NONE, getResources().getString(R.string.url_icon)+url)
      .setIntent(browserLaunchUrlIntent);        	
      c.moveToNext();
    }
    // Done using the cursor, so close it
    c.close();
    // TODO: Determine if any links should be shown here
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
      SQLiteDatabase db = getYammerService().yammerData.getReadableDatabase();
      Cursor c = db.rawQuery(sql, null);
      c.moveToFirst();	
      // Retrieve the message ID og the message clicked on
      int columnIndex = c.getColumnIndex(YammerDataConstants.MESSAGE_ID);
      if (DEBUG) Log.d(TAG_Y, "columnIndex: " + columnIndex);
      final long messageId = c.getLong(columnIndex);
      columnIndex = c.getColumnIndex(YammerDataConstants.USER_ID);
      final long userId = c.getLong(columnIndex);
      // Which item was selected
      switch ( item.getItemId() ) {

      case MENU_VIEW_MESSAGE:
        if (DEBUG) Log.d(TAG_Y, "MENU_VIEW_MESSAGE selected");
        break;
      case MENU_VIEW_THREAD:
        if (DEBUG) Log.d(TAG_Y, "MENU_VIEW_THREAD selected");				
        break;
      case MENU_REPLY:
        if (DEBUG) Log.d(TAG_Y, "MENU_REPLY selected");				
        // Start the reply activity
        Intent i = new Intent(this, YammerReply.class);
        i.putExtra("messageId", messageId);
        startActivityForResult(i, YAMMER_REPLY_CREATE);        
        break;
      case MENU_DELETE:
        if (DEBUG) Log.d(TAG_Y, "MENU_DELETE selected");
        // Delete the item from the database
        // Send delete request to the database
        new Thread(
            new Runnable() {
              public void run() {
                // Delete it from the server
                if (DEBUG) Log.d(TAG_Y, "Deleting message with ID " + messageId);
                try {
                  showLoadingAnimation(true);
                  getYammerService().deleteMessage(messageId);
                } catch (YammerProxy.AccessDeniedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (YammerProxy.ConnectionProblem e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } finally {
                  showLoadingAnimation(false);									
                }
                if (DEBUG) Log.d(TAG_Y, "Message with ID " + messageId + " deleted!");
              }
            }).start();
        break;
      case MENU_FOLLOW:
        if (DEBUG) Log.d(TAG_Y, "MENU_FOLLOW selected");
        new Thread(
            new Runnable() {
              public void run() {
                // Delete it from the server
                if (DEBUG) Log.d(TAG_Y, "Following user with ID " + userId);
                try {
                  showLoadingAnimation(true);
                  getYammerService().followUser(userId);
                } catch (YammerProxy.AccessDeniedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (YammerProxy.ConnectionProblem e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } finally {
                  showLoadingAnimation(false);									
                }
                if (DEBUG) Log.d(TAG_Y, "User with ID " + userId + " followed!");
              }
            }).start();
        break;
      case MENU_UNFOLLOW:
        if (DEBUG) Log.d(TAG_Y, "MENU_UNFOLLOW selected");
        new Thread(
            new Runnable() {
              public void run() {
                // Delete it from the server
                if (DEBUG) Log.d(TAG_Y, "Unfollowing user with ID " + userId);
                try {
                  showLoadingAnimation(true);
                  getYammerService().unfollowUser(userId);
                } catch (YammerProxy.AccessDeniedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (YammerProxy.ConnectionProblem e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } finally {
                  showLoadingAnimation(false);									
                }
                if (DEBUG) Log.d(TAG_Y, "User with ID " + userId + " unfollowed!");
              }
            }).start();
        break;
      case MENU_URL:
        Intent intent = item.getIntent();
        this.startActivity(intent);
        break;
      }
      // Close the cursor
      c.close();
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
    if (DEBUG) Log.d(TAG_Y, "Yammer::onActivityResult");
    switch(requestCode) {
    case YAMMER_REPLY_CREATE:
      if ( resultCode == 0 ) {
        if (DEBUG) Log.d(TAG_Y, "YAMMER_REPLY_CREATE");
        Bundle bundle = data.getExtras();
        final String reply = bundle.getString("reply");
        // Get the message ID we replied upon
        final long messageId = bundle.getLong("messageId");
        // Post the message to the network
        new Thread(
            new Runnable() {
              public void run() {
                // Delete it from the server
                try {
                  showLoadingAnimation(true);
                  // Post reply
                  getYammerService().postMessage(reply, messageId);
                  // Update the messages timeline
                  getYammerService().updatePublicMessages();
                } catch (YammerProxy.AccessDeniedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (YammerProxy.ConnectionProblem e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } finally {
                  showLoadingAnimation(false);										
                }
              }
            }).start();
      }
      break;
    case YAMMER_SETTINGS_CREATE:
      if (DEBUG) Log.d(TAG_Y, "YAMMER_SETTINGS_CREATE: result = " + resultCode);
      if ( resultCode == 0 ) {
      }
      break;
    case YAMMER_BROWSER_CREATE:
      if (DEBUG) Log.d(TAG_Y, "YAMMER_BROWSER_CREATE: result = " + resultCode);
      if ( resultCode == -1 ) {
        if (DEBUG) Log.d(TAG_Y, "Authentication in browser was canceled");
        if ( getYammerService() != null ) {
          YammerService.isAuthenticating = false;
          getYammerService().cancelAuthorization();
        }
        updateAuthenticationUI();
      }
      break;
    default:
      break;
    }
  }

  private void updateAuthenticationUI() {
    if ( YammerService.isAuthenticating == true ) {
      // Authenticating, so just do nothing (progress should be showing already)
      return;
    } else if ( YammerService.isAuthorized() == false ) {
      // We must authenticate
      sendBroadcast("com.yammer:MUST_AUTHENTICATE_DIALOG");
    } else { 
      // Initialize the tweets view
      sendBroadcast("com.yammer:TIMELINE_INITIALIZE");
    }		
  }

  private ServiceConnection mConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      if (DEBUG) Log.d(TAG_Y, "ServiceConnection::onServiceConnected");
      mYammerService = ((YammerService.YammerBinder)service).getService();
      if (mYammerService == null) return;
      updateAuthenticationUI();
    }

    public void onServiceDisconnected(ComponentName className) {
      if (DEBUG) Log.d(TAG_Y, "ServiceConnection::onServiceDisconnected");
      mYammerService = null;
    }
  };

  public Object onRetainNonConfigurationInstance() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::onRetainNonConfigurationInstance");
    return super.onRetainNonConfigurationInstance();
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    if (DEBUG) Log.d(TAG_Y, "Yammer::onCreate");
    super.onCreate(savedInstanceState);
    if (DEBUG) Log.d(TAG_Y, "onCreate::savedInstance: " + savedInstanceState);

    //setTheme(android.R.style.Theme_Black_NoTitleBar);
    setContentView(R.layout.main);

    // Register supported intents
    if (DEBUG) Log.d(TAG_Y, "Registering intents for Yammer");
    IntentFilter filter = new IntentFilter();
    filter.addAction("com.yammer:PUBLIC_TIMELINE_UPDATED");
    filter.addAction("com.yammer:TIMELINE_INITIALIZE");
    filter.addAction("com.yammer:AUTHORIZATION_INITIATE");
    filter.addAction("com.yammer:AUTHORIZATION_START");
    filter.addAction("com.yammer:AUTHORIZATION_DONE");
    filter.addAction("com.yammer:AUTHORIZATION_BROWSER");
    filter.addAction("com.yammer:MUST_AUTHENTICATE_DIALOG");
    filter.addAction("com.yammer:NETWORK_ERROR_MINOR");
    filter.addAction("com.yammer:NETWORK_ERROR_FATAL");
    yammerIntentReceiver = new YammerIntentReceiver();        
    registerReceiver(yammerIntentReceiver, filter);

    // Setup a clicklistener for the message textedit
    final EditText tweetEditor = (EditText)findViewById(R.id.tweet_editor);
    tweetEditor.setOnKeyListener(new OnKeyListener() {
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if ( keyCode == KeyEvent.KEYCODE_ENTER  ) {
          // We only want to post the message, when key is released
          if ( event.getAction() == KeyEvent.ACTION_UP ) {
            final String message = tweetEditor.getText().toString();
            if (DEBUG) Log.d(TAG_Y, "POST MESSAGE: " + message);
            // Post the message to the network
            new Thread(
                new Runnable() {
                  public void run() {
                    try {
                      showLoadingAnimation(true);
                      // Post new message
                      getYammerService().postMessage(message, 0);
                      // Update the messages timeline
                      getYammerService().updatePublicMessages();
                    } catch (YammerProxy.AccessDeniedException e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                    } catch (YammerProxy.ConnectionProblem e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                    } finally {
                      showLoadingAnimation(false);    										
                    }
                  }
                }).start();
            // Remove message from tweet editor
            tweetEditor.setText("");
            toggleTextOverlay(tweetEditor);
          }
          return true;
        }
        return false;
      }
    });

    // Overlay on top of EditText when no text was entered
    LayoutInflater factory = LayoutInflater.from(YammerActivity.this);
    this.noTextOverlayView = factory.inflate(R.layout.no_text_overlay, null);
    RelativeLayout.LayoutParams layoutParams = 
      new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    addContentView(this.noTextOverlayView, layoutParams);	

    // Set key listener used to display "what are you working on" overlay
    tweetEditor.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View _view, MotionEvent _event) {
        if (DEBUG) Log.d(TAG_Y, "tweetEditor::onTouch");
        noTextOverlayView.setVisibility(EditText.INVISIBLE);
        return false;
      }
    });

  }

  private void toggleTextOverlay(View textView) {
    if (DEBUG) Log.d(TAG_Y, "TweetEditor text length: " + ((EditText)textView).getText().length());
    if ( ((EditText)textView).getText().length() > 0 ) {
      noTextOverlayView.setVisibility(EditText.INVISIBLE);
    } else {
      noTextOverlayView.setVisibility(EditText.VISIBLE);		                    	
    }	    									
  }

  @Override
  public void onStart() {
    super.onStart();
    if (DEBUG) Log.d(TAG_Y, "Yammer::onStart");
    // Binding to Yammer service to be able to access service
    if (DEBUG) Log.d(TAG_Y, "Binding to Yammer service");
    bindService( 	new Intent(YammerActivity.this, YammerService.class), 
        mConnection, 
        Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onResume() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::onResume");
    super.onResume();
    try {
      // Show text overlay?
      final EditText tweetEditor = (EditText)findViewById(R.id.tweet_editor);
      toggleTextOverlay(tweetEditor);    		
    } catch(Exception e) {
      e.printStackTrace();
    }

    if ( getYammerService() != null ) {
      getYammerService().resetMessageCount();
    } else {
      if (DEBUG) Log.d(TAG_Y, "mYammerService was null - could not do onResume tasks for YammerService");
    }
  }

  @Override
  public void onPause() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::onPause");
    super.onPause();
  }

  @Override
  public void onDestroy() {
    if (DEBUG) Log.d(TAG_Y, "Yammer::onDestroy");
    if ( yammerIntentReceiver != null ) {
      if (DEBUG) Log.d(TAG_Y, "Unregistering receiver");
      unregisterReceiver(yammerIntentReceiver);
      yammerIntentReceiver = null;
    }    	
    if ( isFinishing() ){
      if (DEBUG) Log.i(TAG_Y, "Activity is finishing");
    }
    //Debug.stopMethodTracing();
    super.onDestroy();
  }

  public void onStop() {
    super.onStop();
    // mYammerService may be null here if keyboard is opened, closed very fast
    if ( getYammerService() != null ) {
      // Reset the message count - we probably saw any new message
      getYammerService().resetMessageCount();
    }
    if (DEBUG) Log.d(TAG_Y, "Yammer::onStop");
    // Need to unbind the service
    if (DEBUG) Log.d(TAG_Y, "Unbinding ServiceConnection");    	
    unbindService(mConnection);
    // TODO: Unregister receiver
    // Make sure intent receiver was registered before unregistering it
  }

  public void showHeaderForFeed(String _feed) {
    EditText editor = (EditText)findViewById(R.id.tweet_editor);
    TextView header = (TextView)findViewById(R.id.feed_label);

    if(YammerProxy.DEFAULT_FEED.equals(_feed)) {
      header.setVisibility(View.GONE);
      editor.setVisibility(View.VISIBLE);
      noTextOverlayView.setVisibility(View.VISIBLE);
    } else {
      editor.setVisibility(View.GONE);
      noTextOverlayView.setVisibility(View.GONE);
      header.setText(_feed+':');
      header.setVisibility(View.VISIBLE);
    }
  }

  private void sendBroadcast(String _intent) {
    sendBroadcast(new Intent(_intent));
  }
}
