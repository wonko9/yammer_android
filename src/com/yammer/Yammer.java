package com.yammer;

import static android.provider.BaseColumns._ID;

import static com.yammer.YammerDataConstants.FULL_NAME;
import static com.yammer.YammerDataConstants.IS_FOLLOWING;
import static com.yammer.YammerDataConstants.MESSAGE;
import static com.yammer.YammerDataConstants.MESSAGE_ID;
import static com.yammer.YammerDataConstants.URL;
import static com.yammer.YammerDataConstants.USER_ID;

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
import android.text.Editable;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnKeyListener;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class Yammer extends Activity {
	
	private static final String TAG_Y = "Yammer";
	
	private YammerService mYammerService = null;
	private SQLiteDatabase db = null;;
    private YammerIntentReceiver yammerIntentReceiver = null;
    boolean listViewInitialized = false;
    private static final String[] PROJECTION = new String[] {MESSAGE};
    // Whenever something starts to load, this will be increased 
    // by 1 - when loading stops, the counter is decreased and 
    // if it reaches zero, the loading animation disappears.
    private static int loadingRefCounter = 0;
    private View noTextOverlayView = null; 
    private final Semaphore loadingRefCounterSemaphore = new Semaphore(1);

    private YammerService getYammerService() {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::getYammerService()");
    	if ( mYammerService == null ) {
    		bindService( 	new Intent(Yammer.this, YammerService.class), 
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
        @Override
        public void onReceive(Context context, Intent intent) {
        	/**
        	 * Launch the browser and let the user authenticate himself
        	 * and activate the application.
        	 */
    		if (G.DEBUG) Log.d(TAG_Y, "Intent received: " + intent.getAction());
        	if ( intent.getAction().equals("com.yammer:PUBLIC_TIMELINE_UPDATED") ) {
        		// Only allow updating the listview if it has been initialized
        		if ( listViewInitialized ) {
            		updateListView();
        		}
        	} else if ( intent.getAction().equals("com.yammer:TIMELINE_INITIALIZE") ) {				
                if (G.DEBUG) Log.d(TAG_Y, "Setting edit text focusable");
		        // Configure view with adapter
                final TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
            	db = getYammerService().yammerData.getReadableDatabase();
            	if (G.DEBUG) Log.d(TAG_Y, "Querying for known messages in network");

            	String myFeed = "";
            	if (YammerSettings.getDefaultFeed(Yammer.this) == "my_feed") {
            		myFeed = "(users.is_following='1' OR messages.user_id='"+getYammerService().getCurrentUserId()+"') AND";
            	}
            	
            	String sql = 
					"select messages._id, messages.message, messages.message_id, messages.timestamp, users.mugshot_url, users.mugshot_md5, users.full_name, users.is_following, users.email, u1.full_name as replyee_full_name, u1.email as replyee_email from messages " + 
					"left join users on users.user_id=messages.user_id " +
					"left join messages as m1 on messages.replied_to_id=m1.message_id " + 
					"left join users as u1 on u1.user_id=m1.user_id where messages.deleted='0' AND "+myFeed+" messages.network_id='"+getYammerService().getCurrentNetworkId()+"' order by messages.message_id desc";
				Cursor cursor = db.rawQuery(sql, null);
				cursor.moveToFirst();

				if (G.DEBUG) Log.d(TAG_Y, "Creating new TweetListAdapter");
            	TweetListAdapter tweetListAdapter = new TweetListAdapter(Yammer.this, R.layout.list_row, cursor, PROJECTION, new int[] {R.id.label} );
                if (G.DEBUG) Log.d(TAG_Y, "Binding adapter to list: " + tweetListView);
                tweetListView.setAdapter(tweetListAdapter);
                startManagingCursor(cursor);                	

                // Register tweetlistview for context menu clicks
                if (G.DEBUG) Log.d(TAG_Y, "Registering tweet list view to receive context menu events");
                registerForContextMenu(tweetListView);
                // Attach an onclick listener to the listview
                tweetListView.setOnItemClickListener( new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> adapterView, View view, int id, long row) {
						if ( YammerSettings.getMessageClickBehaviour(Yammer.this).equals("reply") ) {
							if (G.DEBUG) Log.d(TAG_Y, "Replying to message");
    						long rowId = row;
    				        String sql = "select _id, message_id from messages where " + _ID + "=" + rowId;
    				        SQLiteDatabase db = getYammerService().yammerData.getReadableDatabase();
    				        Cursor c = db.rawQuery(sql, null);
    				        c.moveToFirst();
    						// Just show the reply activity 
    				        Intent i = new Intent(Yammer.this, YammerReply.class);
    				        // Post the message ID being replied upon along with the intent
    				        int columnIndex = c.getColumnIndex(MESSAGE_ID);
    				        if (G.DEBUG) Log.d(TAG_Y, "columnIndex: " + columnIndex);
    				        long messageId = c.getLong(columnIndex);
    				        i.putExtra("messageId", messageId);
    				        startActivityForResult(i, YAMMER_REPLY_CREATE);
    				        c.close();    							
						} else {
							if (G.DEBUG) Log.d(TAG_Y, "Viewing message");    							
							// Create activity YammerSettings
					        Intent i = new Intent(Yammer.this, YammerMessage.class);
					        // We use startActivityForResult because we want to know when
					        // the authorization has completed. If startActivity is used,
					        // no result can be delivered back - it is fire and forget.
					        startActivityForResult(i, 1);
						}
					}                	
                });
                                
                // Set the selector
                tweetListView.setSelector(R.layout.list_row_selector);
                tweetListView.setDividerHeight(1);
                // Create loader wheel 
                createLoaderWheelView();
				// Should loader wheel display (i.e. a loading operation is in progress)
                displayLoaderWheel();                
		        if (G.DEBUG) Log.d(TAG_Y, "ListViewInitialized");
                listViewInitialized = true;
        	} else if ( intent.getAction().equals("com.yammer:MUST_AUTHENTICATE_DIALOG") ) {
        		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer::MUST_AUTHENTICATE_DIALOG");
        		try {
        			removeDialog(ID_DIALOG_LOADING);
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        		showDialog(ID_DIALOG_MUST_AUTHENTICATE);
        	} else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_INITIATE") ) {
        		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_INITIATE");
        		// Hide the authenticate dialog
        		removeDialog(ID_DIALOG_MUST_AUTHENTICATE);
        		// Start the authorization
        		getYammerService().initiateAuthorization();
        	} else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_START") ) {
        		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_START");
        		// Show progress dialog
        		showDialog(ID_DIALOG_LOADING);
        	} else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_DONE") ) {
        		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_DONE");
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
    				    			Intent initIntent = new Intent( "com.yammer:TIMELINE_INITIALIZE" );
    				    			sendBroadcast(initIntent);
    				    			runOnUiThread( new Runnable() {
										@Override
										public void run() {
	        				    			updateListView();        		    				    				
										}
    				    			});
    							} catch (NWOAuthConnectionProblem e) {
    								// TODO Auto-generated catch block
    								e.printStackTrace();
    							} catch (NWOAuthAccessDeniedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} finally {
					        		//if ( G.DEBUG ) Log.d(TAG_Y, "REMOVE DIALOG LOADING");									
									showLoadingAnimation(false);
								}
    						}
    					}).start();
        	} else if ( intent.getAction().equals("com.yammer:AUTHORIZATION_BROWSER") ) {
        		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:AUTHORIZATION_BROWSER");
        		if ( getYammerService() != null && YammerService.isAuthenticating == true ) {
	        		if ( G.DEBUG ) Log.d(TAG_Y, "REMOVE DIALOG LOADING");
        			removeDialog(ID_DIALOG_LOADING);
            		if ( G.DEBUG ) Log.d(TAG_Y, "Starting browser");
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
            		if ( G.DEBUG ) Log.d(TAG_Y, "Browser not starting - authentication not in progress");        			
        		}
	    	} else if ( intent.getAction().equals("com.yammer:NETWORK_ERROR_MINOR") ) {
	    		/**
	    		 * A minor error occurred (connection lost or similar - can be retried later)
	    		 * no need to notify the user.
	    		 */
	    		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:NETWORK_ERROR_MINOR");
	    	} else if ( intent.getAction().equals("com.yammer:NETWORK_ERROR_FATAL") ) {
	    		if ( G.DEBUG ) Log.d(TAG_Y, "com.yammer:NETWORK_ERROR_FATAL");
	    		try {
	    			dismissDialog(ID_DIALOG_LOADING);
	    		} catch (Exception e) {
	    			// 
	    		}
	    		/**
	    		 * A fatal network error has occured - user must be notified
	    		 */
	    		AlertDialog.Builder builder = new AlertDialog.Builder(Yammer.this);
	    		AlertDialog alertDialog = builder.create();
	    		alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
	    		alertDialog.setTitle("Error!");
	    		alertDialog.setMessage("A network error occured! Try again later.");
	    		alertDialog.setOnDismissListener(new OnDismissListener() {
					@Override
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
			if (G.DEBUG) Log.d(TAG_Y, "Showing loader wheel: " + view);
			// Something must be loading, so show the view
			view.setVisibility(View.VISIBLE);
		} else {
			Log.d(TAG_Y, "view.isShown: " + view.isShown());
			if ( view.isShown() ) {
				if (G.DEBUG) Log.d(TAG_Y, "Hiding loader wheel: " + view);
				// Nothing is loading, so remove the view
				view.setVisibility(View.INVISIBLE);				
			} else {
				if (G.DEBUG) Log.d(TAG_Y, "Hiding new loader wheel (thread transit): " + cVire);
				if (cVire != null) cVire.setVisibility(View.INVISIBLE);				
			}
		}		
	}
	
	protected void showLoadingAnimation(final boolean enabled) {
		runOnUiThread( new Runnable() {
			@Override
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
					if (G.DEBUG) Log.d(TAG_Y, "loadingRefCounter: " + loadingRefCounter);
					displayLoaderWheel();
				} finally {
					// Release mutex
					loadingRefCounterSemaphore.release();
				}
			}
		});
	}

	protected View createLoaderWheelView() {
		if (G.DEBUG) Log.d(TAG_Y, "Yammer::createLoaderWheelView");
	    View loaderWheelView = (ImageView)findViewById(R.id.loader_animation_overlay); 
		if ( loaderWheelView == null /*Loader wheel view not shown yet*/ ) {
			if (G.DEBUG) Log.d(TAG_Y, "loaderWheelView doesn't exist, so creating it.");
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
		if (G.DEBUG) Log.d(TAG_Y, "Yammer::onCreateDialog("+id+")");
		if ( id == ID_DIALOG_MUST_AUTHENTICATE ) {
    		// Show "Start Yammer Authentication" dialog
    		AuthenticateDialog authDialog = new AuthenticateDialog(Yammer.this);
    		authDialog.setCancelable(true);
    		authDialog.setOnCancelListener(new OnCancelListener() {
				@Override
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
				@Override
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
				    		Intent intent = new Intent( "com.yammer:RESET_ACCOUNT" );
				    		sendBroadcast(intent);													
						}
					}
				}
    		});
	        return progressDialog;
		} else if ( id == ID_DIALOG_ERROR_FATAL ) {
			// 
		} else if ( id == ID_DIALOG_FEEDS ) {
			// Default selected item = "all_messages"
			int selectedItem = 0;
			// Which item selected?
			if ( YammerSettings.getDefaultFeed(this).equals("my_feed") ) {
				selectedItem = 1;
			} 
			return new AlertDialog.Builder(Yammer.this)
            .setTitle("Select Default Feed")
            .setIcon(R.drawable.yammer_logo_medium)
            .setSingleChoiceItems(R.array.settings_feed_entries, selectedItem, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	if (whichButton == 0) {
                    	if (G.DEBUG) Log.d(TAG_Y, "Feed 'all_messages' selected" );                	
                    	YammerSettings.setDefaultFeed(Yammer.this, "all_messages");
            			Intent initIntent = new Intent( "com.yammer:TIMELINE_INITIALIZE" );
            			sendBroadcast(initIntent);			
                	} else {
                    	if (G.DEBUG) Log.d(TAG_Y, "Feed 'my_feed' selected" );                	
                    	YammerSettings.setDefaultFeed(Yammer.this, "my_feed");
            			Intent initIntent = new Intent( "com.yammer:TIMELINE_INITIALIZE" );
            			sendBroadcast(initIntent);			
                	}
                }
            })
            /*.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	// Get selected item
                	if (G.DEBUG) Log.d(TAG_Y, "whichButton: " + whichButton );                	
                	// Set default feed
                	YammerSettings.setDefaultFeed(Yammer.this, "my_feed");
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            })*/
           .create();		
		}
		return super.onCreateDialog(id);
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_FEEDS, Menu.NONE, R.string.feeds).setIcon(R.drawable.menu_show_list);
    	menu.add(0, MENU_RELOAD, Menu.NONE, R.string.reload).setIcon(R.drawable.menu_refresh);
    	menu.add(0, MENU_SETTINGS, Menu.NONE, R.string.settings).setIcon(R.drawable.menu_preferences);
    	return (super.onCreateOptionsMenu(menu));
    }
        
    public void updateListView() {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::updateListView");
        try {
            // Reconfigure the list view
            TweetListView tweetListView = (TweetListView) findViewById(R.id.tweet_list);
            TweetListAdapter tweetListAdapter = (TweetListAdapter)tweetListView.getAdapter();
            SQLiteCursor cursor = (SQLiteCursor)tweetListAdapter.getCursor();
        	cursor.requery();
            tweetListAdapter.notifyDataSetChanged();
    		// If we are at the top of the screen, then show the newest item
            if ( tweetListView.getFirstVisiblePosition() == 0 ) {
            	if (G.DEBUG) Log.d(TAG_Y, "Scrolling view to top");
            	// Scroll to top
            	tweetListView.setSelectionFromTop(0, 0);
            } else {
                // If looking at messages further down, show a notification                	
            	//if (G.DEBUG) Log.d(TAG_Y, "Notifying user of new message");
            	//Toast.makeText(Yammer.this, "New message", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
        	// Caught the cursor at the wrong time
        	e.printStackTrace();
        	if (G.DEBUG) Log.d(TAG_Y, "Whoops.. Cursor or view wasn't valid. Makes no sense to continue.");        	
        	return;        	
        }
    }
    
    @Override 
    public boolean onOptionsItemSelected(MenuItem item) {
    	// Which item was selected
		switch ( item.getItemId() ) {
			case MENU_RELOAD:
				if (G.DEBUG) Log.d(TAG_Y, "MENU_RELOAD selected");
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
									Intent intent = new Intent( "com.yammer:PUBLIC_TIMELINE_UPDATED" );
									sendBroadcast(intent);
								} catch (NWOAuthAccessDeniedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (NWOAuthConnectionProblem e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} finally {
									showLoadingAnimation(false);									
								}
							}
						}).start();
				break;
			case MENU_SETTINGS:
				if (G.DEBUG) Log.d(TAG_Y, "MENU_SETTINGS selected");
				// Create activity YammerSettings
		        Intent i = new Intent(this, YammerSettings.class);
		        // We use startActivityForResult because we want to know when
		        // the authorization has completed. If startActivity is used,
		        // no result can be delivered back - it is fire and forget.
		        startActivityForResult(i, YAMMER_SETTINGS_CREATE);        
				break;
			case MENU_FEEDS:
				if (G.DEBUG) Log.d(TAG_Y, "MENU_FEEDS selected");
				// Create activity YammerSettings
				showDialog(ID_DIALOG_FEEDS);
				break;
		};
    	return (super.onOptionsItemSelected(item));  
    }
    
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (G.DEBUG) Log.d(TAG_Y, "Create context menu");
		// Get the row ID
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
		long rowId = info.id;
		// Select
        String sql = "select messages._id, messages.message, messages.message_id, messages.user_id, users.full_name, users.is_following, urls.url from messages join users on messages.user_id=users.user_id left join urls on messages.message_id=urls.message_id where messages." + _ID + "=" + rowId;
        SQLiteDatabase db = getYammerService().yammerData.getReadableDatabase();
        Cursor c = db.rawQuery(sql, null);
        c.moveToFirst();
        // Get the user ID of the user who  posted the message
        int columnIndex = c.getColumnIndex(USER_ID);
        long userId = c.getLong(columnIndex);
        // Get the message posted
        columnIndex = c.getColumnIndex(MESSAGE);
        String message = c.getString(columnIndex);
        // Get the full name of the user who posted the message
        columnIndex = c.getColumnIndex(FULL_NAME);
        String fullName = c.getString(columnIndex);
        // Get the full name of the user who posted the message
        columnIndex = c.getColumnIndex(IS_FOLLOWING);
        int isFollowing = c.getInt(columnIndex);
        //menu.setHeaderTitle(R.string.popup_title_label);
        // Is this my own message?
        boolean myself = false;
		if ( userId == getYammerService().getCurrentUserId() ) {
			myself = true;
		}
		// Start building the menu
        menu.setHeaderTitle(message);   
        menu.setHeaderIcon(R.drawable.icon_small);
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
			menu.add(0, MENU_DELETE, ContextMenu.NONE, R.string.delete_label).setIcon(R.drawable.icon_small);
		}
		
		// Submenu for URLs
		//Menu urlSubMenu = null;
		if (G.DEBUG) Log.d(TAG_Y, "c.getCount(): " + c.getCount());
		// Add any URL's that may have been enclosed in the message
        columnIndex = c.getColumnIndex(URL);
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
            if (G.DEBUG) Log.d(TAG_Y, "URL: " + url);
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
	        int columnIndex = c.getColumnIndex(MESSAGE_ID);
	        if (G.DEBUG) Log.d(TAG_Y, "columnIndex: " + columnIndex);
	        final long messageId = c.getLong(columnIndex);
	        columnIndex = c.getColumnIndex(USER_ID);
	        final long userId = c.getLong(columnIndex);
	        // Which item was selected
			switch ( item.getItemId() ) {

				case MENU_VIEW_MESSAGE:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_VIEW_MESSAGE selected");
					break;
				case MENU_VIEW_THREAD:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_VIEW_THREAD selected");				
					break;
				case MENU_REPLY:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_REPLY selected");				
					// Start the reply activity
			        Intent i = new Intent(this, YammerReply.class);
			        i.putExtra("messageId", messageId);
			        startActivityForResult(i, YAMMER_REPLY_CREATE);        
					break;
				case MENU_DELETE:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_DELETE selected");
					// Delete the item from the database
					// Send delete request to the database
					new Thread(
							new Runnable() {
								public void run() {
									// Delete it from the server
									if (G.DEBUG) Log.d(TAG_Y, "Deleting message with ID " + messageId);
									try {
										showLoadingAnimation(true);
										getYammerService().deleteMessage(messageId);
									} catch (NWOAuthAccessDeniedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (NWOAuthConnectionProblem e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} finally {
										showLoadingAnimation(false);									
									}
									if (G.DEBUG) Log.d(TAG_Y, "Message with ID " + messageId + " deleted!");
								}
							}).start();
					break;
				case MENU_FOLLOW:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_FOLLOW selected");
					new Thread(
							new Runnable() {
								public void run() {
									// Delete it from the server
									if (G.DEBUG) Log.d(TAG_Y, "Following user with ID " + userId);
									try {
										showLoadingAnimation(true);
										getYammerService().followUser(userId);
									} catch (NWOAuthAccessDeniedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (NWOAuthConnectionProblem e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} finally {
										showLoadingAnimation(false);									
									}
									if (G.DEBUG) Log.d(TAG_Y, "User with ID " + userId + " followed!");
								}
							}).start();
					break;
				case MENU_UNFOLLOW:
					if (G.DEBUG) Log.d(TAG_Y, "MENU_UNFOLLOW selected");
					new Thread(
							new Runnable() {
								public void run() {
									// Delete it from the server
									if (G.DEBUG) Log.d(TAG_Y, "Unfollowing user with ID " + userId);
									try {
										showLoadingAnimation(true);
										getYammerService().unfollowUser(userId);
									} catch (NWOAuthAccessDeniedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (NWOAuthConnectionProblem e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} finally {
										showLoadingAnimation(false);									
									}
									if (G.DEBUG) Log.d(TAG_Y, "User with ID " + userId + " unfollowed!");
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

		AlertDialog.Builder builder = new AlertDialog.Builder(Yammer.this);
		AlertDialog alertDialog = builder.create();
		alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
		alertDialog.setTitle("Error!");
		alertDialog.setMessage(errorMessage);			
		alertDialog.show();
		return false;			
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (G.DEBUG) Log.d(TAG_Y, "Yammer::onActivityResult");
		switch(requestCode) {
			case YAMMER_REPLY_CREATE:
				if ( resultCode == 0 ) {
					if (G.DEBUG) Log.d(TAG_Y, "YAMMER_REPLY_CREATE");
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
									} catch (NWOAuthAccessDeniedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (NWOAuthConnectionProblem e) {
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
				if (G.DEBUG) Log.d(TAG_Y, "YAMMER_SETTINGS_CREATE: result = " + resultCode);
				if ( resultCode == 0 ) {
				}
				break;
			case YAMMER_BROWSER_CREATE:
				if (G.DEBUG) Log.d(TAG_Y, "YAMMER_BROWSER_CREATE: result = " + resultCode);
				if ( resultCode == -1 ) {
					if (G.DEBUG) Log.d(TAG_Y, "Authentication in browser was canceled");
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
    		Intent authenticateIntent = new Intent( "com.yammer:MUST_AUTHENTICATE_DIALOG" );
    		sendBroadcast(authenticateIntent);
		} else { 
			// Initialize the tweets view
			Intent initIntent = new Intent( "com.yammer:TIMELINE_INITIALIZE" );
			sendBroadcast(initIntent);
		}		
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			if (G.DEBUG) Log.d(TAG_Y, "ServiceConnection::onServiceConnected");
			mYammerService = ((YammerService.YammerBinder)service).getService();
			if (mYammerService == null) return;
			updateAuthenticationUI();
		}

		public void onServiceDisconnected(ComponentName className) {
			if (G.DEBUG) Log.d(TAG_Y, "ServiceConnection::onServiceDisconnected");
			mYammerService = null;
		}
	};

	public Object onRetainNonConfigurationInstance() {
		if (G.DEBUG) Log.d(TAG_Y, "Yammer::onRetainNonConfigurationInstance");
		return super.onRetainNonConfigurationInstance();
	}
		
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onCreate");
    	//Debug.startMethodTracing("traceview");
    	super.onCreate(savedInstanceState);
    	if (G.DEBUG) Log.d(TAG_Y, "onCreate::savedInstance: " + savedInstanceState);
        //setTheme(android.R.style.Theme_Black_NoTitleBar);
        setContentView(R.layout.main);
        // Register supported intents
        if (G.DEBUG) Log.d(TAG_Y, "Registering intents for Yammer");
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
        				if (G.DEBUG) Log.d(TAG_Y, "POST MESSAGE: " + message);
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
    									} catch (NWOAuthAccessDeniedException e) {
    										// TODO Auto-generated catch block
    										e.printStackTrace();
    									} catch (NWOAuthConnectionProblem e) {
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
		final TextKeyListener listener = TextKeyListener.getInstance(true, TextKeyListener.Capitalize.SENTENCES);

		// Overlay on top of EditText when no text was entered
        LayoutInflater factory = LayoutInflater.from(Yammer.this);
		noTextOverlayView = factory.inflate(R.layout.no_text_overlay, null);
		RelativeLayout.LayoutParams layoutParams = 
				new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		addContentView(noTextOverlayView, layoutParams);	

		// Fade in the Yammer overlay
		View yammerOverlayView = factory.inflate(R.layout.yammer_overlay, null);
		layoutParams = 
				new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		addContentView(yammerOverlayView, layoutParams);
		
        // Fade away the nullwire logo
        Animation animation = AnimationUtils.loadAnimation(Yammer.this, R.drawable.alpha_fadein);
		findViewById(R.id.yammer_logo_overlay).startAnimation(animation);

		// Set key listener used to display "what are you working on" overlay
		tweetEditor.setKeyListener(new TextKeyListener(TextKeyListener.Capitalize.SENTENCES, true) {
				@Override
				public boolean onKeyDown(View textView, Editable content, int keyCode, KeyEvent event) {
					if (G.DEBUG) Log.d(TAG_Y, "editTextView::onKeyDown");
					listener.onKeyDown(textView, content, keyCode, event);
					toggleTextOverlay(textView);
					return false;
				}
	        }
        );
        try {
	        if ( G.IS_BETA ) {
		        // Stuff to do id this is a beta
	        	// e.g. show "beta" overlay
	        }
        } catch (Exception e) {
        	if (G.DEBUG) Log.d(TAG_Y, "Error while creating BETA overlay");
        	e.printStackTrace();
        }
    }
    
	private void toggleTextOverlay(View textView) {
		if (G.DEBUG) Log.d(TAG_Y, "TweetEditor text length: " + ((EditText)textView).getText().length());
		if ( ((EditText)textView).getText().length() > 0 ) {
        	noTextOverlayView.setVisibility(EditText.INVISIBLE);
        } else {
        	noTextOverlayView.setVisibility(EditText.VISIBLE);		                    	
        }	    									
	}

	@Override
    public void onStart() {
    	super.onStart();
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onStart");
        // Binding to Yammer service to be able to access service
    	if (G.DEBUG) Log.d(TAG_Y, "Binding to Yammer service");
        bindService( 	new Intent(Yammer.this, YammerService.class), 
        				mConnection, 
        				Context.BIND_AUTO_CREATE);
	}
    
    @Override
    public void onResume() {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onResume");
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
    		if (G.DEBUG) Log.d(TAG_Y, "mYammerService was null - could not do onResume tasks for YammerService");
    	}
    }
    
    @Override
    public void onPause() {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onPause");
    	super.onPause();
    }
    
    @Override
    public void onDestroy() {
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onDestroy");
    	if ( yammerIntentReceiver != null ) {
    		if (G.DEBUG) Log.d(TAG_Y, "Unregistering receiver");
    		unregisterReceiver(yammerIntentReceiver);
    		yammerIntentReceiver = null;
    	}    	
    	if ( isFinishing() ){
    		if (G.DEBUG) Log.i(TAG_Y, "Activity is finishing");
    	}
    	//Debug.stopMethodTracing();
    	super.onDestroy();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	// mYammerService may be null here if keyboard is opened, closed very fast
    	if ( getYammerService() != null ) {
        	// Reset the message count - we probably saw any new message
    		getYammerService().resetMessageCount();
    	}
    	if (G.DEBUG) Log.d(TAG_Y, "Yammer::onStop");
    	// Need to unbind the service
    	if (G.DEBUG) Log.d(TAG_Y, "Unbinding ServiceConnection");    	
    	unbindService(mConnection);
    	// TODO: Unregister receiver
    	// Make sure intent receiver was registered before unregistering it
    }
}
