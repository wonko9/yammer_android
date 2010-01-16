package com.yammer.v1.settings;

import java.util.Date;

import com.yammer.v1.G;
import com.yammer.v1.R;
import com.yammer.v1.YammerProxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsEditor {
  
  private static final boolean DEBUG = G.DEBUG;

  private Context context;
  
  public SettingsEditor(Context _ctx) {
    this.context = _ctx;
  }
  
  private SharedPreferences getPreferences() {
    return PreferenceManager.getDefaultSharedPreferences(this.context);
  }

  public boolean startServiceAtBoot() {
    if(DEBUG) Log.d(getClass().getName(), ".startServiceAtBoot");
    return getPreferences().getBoolean("key_background", true);
  }

  public void setFeed(String feedName) {
    if(DEBUG) Log.d(getClass().getName(), ".setFeed: " + feedName);
    SharedPreferences settings = getPreferences();       
    SharedPreferences.Editor editor = settings.edit();
    editor.putString("key_feed", feedName);
    editor.commit();      
  }

  public String getFeed() {
    String ret = getPreferences().getString("key_feed", YammerProxy.DEFAULT_FEED);
    if(DEBUG) Log.d(SettingsActivity.class.getName(), ".getFeed: " + ret);
    return ret;
  }

  public void setUpdatedAt() {
    setUpdatedAt(new Date());
  }
  
  public void setUpdatedAt(Date time) {
    if(DEBUG) Log.d(getClass().getName(), ".setUpdatedAt: " + time.toString());
    SharedPreferences settings = getPreferences();       
    SharedPreferences.Editor editor = settings.edit();
    editor.putLong("key_updated_at", time.getTime());
    editor.commit();      
  }

  public Date getUpdatedAt() {
    Date time = new Date(getPreferences().getLong("key_updated_at", System.currentTimeMillis()));
    if(DEBUG) Log.d(getClass().getName(), ".getUpdatedAt: " + time.toString());
    return time;
  }

  public String getUrl() {
    String ret = getPreferences().getString("key_url", context.getString(R.string.pref_url_default)); 
    if(DEBUG) Log.d(SettingsActivity.class.getName(), ".getUrl:" + ret);      
    return ret; 
  }

  public long getUpdateTimeout() {
    long ret = Long.parseLong(getPreferences().getString("key_update", "120"))*1000; 
    if(DEBUG) Log.d(getClass().getName(), ".getUpdateTimeout: " + ret);           
    return ret;
  }

  public String getMessageClickBehaviour() {
    String ret = getPreferences().getString("key_replies", "reply");
    if(DEBUG) Log.d(SettingsActivity.class.getName(), "getMessageClickBehavior: " + ret);
    return ret;
  }

  public String getDisplayName() {
    String ret = getPreferences().getString("key_names", "firstname");
    if(DEBUG) Log.d(SettingsActivity.class.getName(), "key_names: " + ret);
    return ret;
  }

  public boolean getVibrate() {
    boolean ret = getPreferences().getBoolean("key_vibrate", true);
    if(DEBUG) Log.d(SettingsActivity.class.getName(), "key_vibrate: " + ret);
    return ret;
  }

}
