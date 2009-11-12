/**
 * (C)Copyright 2009 Yammer.
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

public class YammerSettings extends PreferenceActivity
implements SharedPreferences.OnSharedPreferenceChangeListener  
{

  private static String TAG_YSETTINGS = "YammerSettings";

  protected void onCreate(Bundle savedInstanceState) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onCreate");
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.layout.settings);
  }

  protected void onResume() {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onResume");
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    super.onResume();
  }

  protected void onPause() {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onPause");
    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  protected void onDestroy() {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::onDestroy");
    super.onDestroy();
  }

  private final int MENU_ABOUT = 2;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_ABOUT, Menu.NONE, R.string.about).setIcon(R.drawable.menu_info_details);
    return (super.onCreateOptionsMenu(menu));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch( item.getItemId() ) {
    case MENU_ABOUT:
      if (G.DEBUG) Log.d(TAG_YSETTINGS, "MENU_ABOUT");
      // Create activity YammerSettings
      Intent i = new Intent(this, AboutActivity.class);
      // We use startActivityForResult because we want to know when
      // the authorization has completed. If startActivity is used,
      // no result can be delivered back - it is fire and forget.
      startActivity(i);
      break;
    }
    return super.onOptionsItemSelected(item);
  }

  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "onSharedPreferenceChange: " + _key);
    if("key_url".equals(_key)) {
      getApplicationContext().sendBroadcast(new Intent( "com.yammer:RESET_ACCOUNT" ));
    }
  }

  public static boolean startServiceAtBoot(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::startService");
    return getPreferences(context).getBoolean("key_background", true);
  }

  public static void setDefaultFeed(Context context, String feedName) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getDefaultFeed");
    SharedPreferences settings = getPreferences(context);				
    SharedPreferences.Editor editor = settings.edit();
    editor.putString("key_feed", feedName);
    editor.commit();    	
  }

  public static String getDefaultFeed(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getDefaultFeed");    	
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_feed: " + getPreferences(context).getString("key_feed", "all_messages"));
    return getPreferences(context).getString("key_feed", "all_messages");
  }

  public static String getUrl(Context context) {
    String ret = getPreferences(context).getString("key_url", context.getString(R.string.pref_url_default)); 
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getUrl:" + ret);      
    return ret; 
  }

  public static long getUpdateTimeout(Context context) {
    //if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getUpdateTimeout");    	    	
    return Long.parseLong(getPreferences(context).getString("key_update", "120"))*1000;
  }

  public static String getMessageClickBehaviour(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "YammerSettings::getMessageClickBehaviour");
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_replies: " + getPreferences(context).getString("key_replies", "reply"));
    return getPreferences(context).getString("key_replies", "reply");
  }

  public static String getDisplayName(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_names: " + getPreferences(context).getString("key_names", "firstname"));
    return getPreferences(context).getString("key_names", "firstname");
  }

  public static boolean getVibrate(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_vibrate: " + getPreferences(context).getBoolean("key_vibrate", true));
    return getPreferences(context).getBoolean("key_vibrate", true);
  }

  public static String getNotificationOn(Context context) {
    if (G.DEBUG) Log.d(TAG_YSETTINGS, "key_notification: " + getPreferences(context).getString("key_notification", "all"));    	
    return getPreferences(context).getString("key_notification", "all");
  }

  private static SharedPreferences getPreferences(Context _ctx) {
    return PreferenceManager.getDefaultSharedPreferences(_ctx);
  }

}
