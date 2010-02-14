package com.yammer.v1;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditAccountPreference extends DialogPreference {

	private static String TAG = "EditAccountPreferences";
    private Context context; 
    
    public EditAccountPreference(Context context, AttributeSet attrs) { 
        super(context, attrs); 
        this.context = context;
        this.setDialogIcon(android.R.drawable.ic_dialog_alert);
    }
    
    protected void onPrepareDialogBuilder(Builder builder) {
    	// Build layout
    	LinearLayout layout = new LinearLayout(context);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)); 
        layout.setMinimumWidth(400); 
        layout.setPadding(10, 10, 10, 10);
        // Build warning text
        TextView warningText = new TextView(context); 
        warningText.setText("Clear all messages and account information? You will have to reauthenticate with Yammer!");
        warningText.setTextColor(0xffffffff);
        warningText.setTextSize(14);
        layout.addView(warningText); 
        builder.setView(layout); 
        super.onPrepareDialogBuilder(builder); 
    } 

    protected void onDialogClosed(boolean positiveResult) { 
        if(positiveResult){
        	if (G.DEBUG) Log.d(TAG, "Instructed to clear account - sending intent");
        	// Clear all account information
    		context.sendBroadcast(new Intent(YammerService.INTENT_RESET_ACCOUNT));
        } 
    } 	
}
