package com.yammer.v1;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.nullwire.trace.ExceptionHandler;

public class YammerApp extends Application {
  
  private static final boolean DEBUG = G.DEBUG;
  
  private static String TAG = "Yammer Android";
  
  static Context context = null;

  @Override
  public void onCreate() {
    super.onCreate();
    if (DEBUG) Log.d(TAG, "YammerApp::onCreate");
    ExceptionHandler.register(this, "http://www.stdmn.com/ss/android/report_exception.php");
    // Get the files path
    G.FILES_PATH = getFilesDir().getAbsolutePath();
    // Get version of application
    PackageManager pm = getPackageManager();
    try {
      PackageInfo pi = pm.getPackageInfo(this.getPackageName(), 0);
      G.APP_VERSION = pi.versionName;
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // Determine if this is a beta application
    // If this is a beta version, show the BETA overlay
    // Start the Yammer service
    if (DEBUG) Log.i(TAG, "Starting Yammer service");
    startService(new Intent(this, YammerService.class));
  }
}
