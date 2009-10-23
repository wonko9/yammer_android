package com.yammer;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.nullwire.trace.ExceptionHandler;

public class Yowl extends Application {
	private static String TAG = "YowlApp";
	static Context context = null;
	@Override
	public void onCreate() {
		super.onCreate();
		if (G.DEBUG) Log.d(TAG, "Yowl::onCreate");
		ExceptionHandler.register(this);
		// Get the files path
		G.FILES_PATH = getFilesDir().getAbsolutePath();
		// Get version of application
		PackageManager pm = getPackageManager();
		try {
			PackageInfo pi = pm.getPackageInfo(this.getPackageName(), 0);
			G.APP_VERSION = pi.versionName;
			String[] versionSplit = pi.versionName.split("\\.");
			if (G.DEBUG) Log.i(TAG, "versionName: " + pi.versionName );
			if (G.DEBUG) Log.i(TAG, "minorVersion: " + versionSplit[2]);
	        if ( Integer.parseInt(versionSplit[2]) % 2  != 0) {
		        // Create and show the BETA overlay
		        G.IS_BETA = true;
	        }
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Determine if this is a beta application
        // If this is a beta version, show the BETA overlay
		// Start the Yammer service
        if (G.DEBUG) Log.i(TAG, "Starting Yammer service");
        startService(new Intent(this, YammerService.class));
	}
}
