/**
 * (C)Copyright 2010 Yammer.
 */

package com.yammer.v1.settings;

import com.yammer.v1.AboutActivity;
import com.yammer.v1.G;
import com.yammer.v1.R;
import com.yammer.v1.YammerService;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity
  implements SharedPreferences.OnSharedPreferenceChangeListener  
{
  
  private static final boolean DEBUG = G.DEBUG;

  protected void onCreate(Bundle savedInstanceState) {
    if(DEBUG) Log.d(getClass().getName(), ".onCreate");
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.layout.settings_activity);
  }

  protected void onResume() {
    if(DEBUG) Log.d(getClass().getName(), ".onResume");
    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    super.onResume();
  }

  protected void onPause() {
    if(DEBUG) Log.d(getClass().getName(), ".onPause");
    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    super.onPause();
  }

  protected void onDestroy() {
    if(DEBUG) Log.d(getClass().getName(), ".onDestroy");
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
    switch(item.getItemId()) {
      case MENU_ABOUT:
        if(DEBUG) Log.d(getClass().getName(), "MENU_ABOUT");
        startActivity(new Intent(this, AboutActivity.class));
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key) {
    if(DEBUG) Log.d(getClass().getName(), "onSharedPreferenceChange: " + _key);
    if("key_url".equals(_key)) {
      getApplicationContext().sendBroadcast(new Intent(YammerService.INTENT_RESET_ACCOUNT));
    }
  }

}
