/**
 * (C)Copyright 2009 Nullwire
 */

package com.yammer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class YammerSettings extends PreferenceActivity {
	
	private static String TAG_YSETTINGS = "YammerSettings";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.settings);
		addPreferencesFromResource(R.layout.settings);
	}

	//private final int MENU_SAVE = 0;
	//private final int MENU_CANCEL = 1;
	private final int MENU_ABOUT = 2;
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	//menu.add(0, MENU_SAVE, Menu.NONE, R.string.save);
    	//menu.add(0, MENU_CANCEL, Menu.NONE, R.string.cancel);
    	menu.add(0, MENU_ABOUT, Menu.NONE, R.string.about).setIcon(R.drawable.menu_info_details);
    	return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch( item.getItemId() ) {
    		case MENU_ABOUT:
    			if (G.DEBUG) Log.d(TAG_YSETTINGS, "MENU_ABOUT");
				// Create activity YammerSettings
		        Intent i = new Intent(this, About.class);
		        // We use startActivityForResult because we want to know when
		        // the authorization has completed. If startActivity is used,
		        // no result can be delivered back - it is fire and forget.
		        startActivity(i);
    			break;
    	}
    	return super.onOptionsItemSelected(item);
    }

    public void onPause() {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onPause");
    	super.onPause();
    }
    
    @Override
    public void onDestroy() {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onDestroy");
    	super.onDestroy();
    }
    
    public static boolean startServiceAtBoot(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::startService");
    	return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("key_background", true);
    }

    public static void setDefaultFeed(Context context, String feedName) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getDefaultFeed");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);				
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("key_feed", feedName);
        editor.commit();    	
    }
    
    public static String getDefaultFeed(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getDefaultFeed");    	
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_feed: " + PreferenceManager.getDefaultSharedPreferences(context).getString("key_feed", "all_messages"));
    	return PreferenceManager.getDefaultSharedPreferences(context).getString("key_feed", "all_messages");
    }

    public static long getUpdateTimeout(Context context) {
    	//if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getUpdateTimeout");    	    	
    	return Long.parseLong(PreferenceManager.getDefaultSharedPreferences(context).getString("key_update", "120"))*1000;
    }
    
    public static String getMessageClickBehaviour(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getMessageClickBehaviour");
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_replies: " + PreferenceManager.getDefaultSharedPreferences(context).getString("key_replies", "reply"));
    	return PreferenceManager.getDefaultSharedPreferences(context).getString("key_replies", "reply");
    }
    
    public static String getDisplayName(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_names: " + PreferenceManager.getDefaultSharedPreferences(context).getString("key_names", "firstname"));
    	return PreferenceManager.getDefaultSharedPreferences(context).getString("key_names", "firstname");
    }

    public static boolean getVibrate(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_vibrate: " + PreferenceManager.getDefaultSharedPreferences(context).getBoolean("key_vibrate", true));
    	return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("key_vibrate", true);
    }
    
    public static String getNotificationOn(Context context) {
    	if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_notification: " + PreferenceManager.getDefaultSharedPreferences(context).getString("key_notification", "all"));    	
    	return PreferenceManager.getDefaultSharedPreferences(context).getString("key_notification", "all");
    }
}
