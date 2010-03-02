package com.yammer.v1;

import com.yammer.v1.settings.SettingsEditor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class YammerServiceManager extends BroadcastReceiver {

  private static final boolean DEBUG = G.DEBUG;

   @Override
   public void onReceive(Context context, Intent intent) {
    if (DEBUG) Log.d(getClass().getName(), ".onReceive: " + intent.getAction());
      // just make sure we are getting the right intent (better safe than sorry)
      if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

         if ( !new SettingsEditor(context).startServiceAtBoot() ) {
            if (DEBUG) Log.d(getClass().getName(), "Yammer configured not to start at boot");
            return;
         }

         ComponentName comp = new ComponentName(context.getPackageName(), YammerService.class.getName());
         ComponentName service = context.startService(new Intent().setComponent(comp));

         if (null == service){
            // something really wrong here
            Log.e(getClass().getName(), "Could not start service " + comp.toString());
         }
      } else {
         Log.e(getClass().getName(), "Received unexpected intent " + intent.toString());
      }
   }
}
