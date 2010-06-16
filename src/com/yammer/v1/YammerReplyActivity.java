/**
 * (C)Copyright 2009, Yammer, Inc.
 */

package com.yammer.v1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;

public class YammerReplyActivity extends Activity {
  
  private static boolean DEBUG = G.DEBUG;
	
	/**
	 * Activity being created
	 */
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		if (DEBUG) Log.d(getClass().getName(), ".onCreate");
		setTheme(android.R.style.Theme_Translucent_NoTitleBar);
		// Make sure that the background is blurred behind the activity
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		setContentView(R.layout.reply);
		// Listen for clicks in the edit box to be able to enable the "reply" button
		EditText replyEdit = (EditText)findViewById(R.id.reply_edit);
		replyEdit.setOnKeyListener(onReplyEditKeyListener);
		// Listen to the cancel button
		Button cancelButton = (Button)findViewById(R.id.reply_cancel);
		cancelButton.setOnClickListener(onCancelListener);
		// Listen to the reply button
		Button replyButton = (Button)findViewById(R.id.reply_post);
		replyButton.setOnClickListener(onReplyListener);
		// Set the state of the reply button
		setReplyButtonState();
		// Set the default result to cancel
		setResult(-1);
		// Hide the Yammer logo
//		findViewById(R.id.share_logo).setVisibility(View.GONE);
	}

	@Override
	public void onStart() {
		if (DEBUG) Log.d(getClass().getName(), ".onStart");
		super.onStart();
	}
	
	@Override
	public void onResume() {
		if (DEBUG) Log.d(getClass().getName(), ".onResume");
		super.onResume();
		// Set reply button state
		setReplyButtonState();
	}
		
	private void setReplyButtonState() {
		// Check if the reply edit is empty
		EditText replyEdit = (EditText)findViewById(R.id.reply_edit);
		Button replyButton = (Button)findViewById(R.id.reply_post);
		String reply = replyEdit.getText().toString();
		if (DEBUG) Log.d(getClass().getName(), "reply.length()="+reply.length());
		if ( reply.length() > 0 ) {
			replyButton.setEnabled(true);
		} else {
			replyButton.setEnabled(false);				
		}		
	}
	
	/**
	 * If the edit box is empty, the reply button must be grayed out.
	 * If text is present in the edit box, the reply button must be 
	 * clickable.
	 */
	 private OnKeyListener onReplyEditKeyListener = new OnKeyListener() {
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			// Reply button only enabled, when something written in the edit box
			setReplyButtonState();
			return false;
		}
	};
	
	/**
	 * Handle clicks on the cancel button
	 */
	private OnClickListener onCancelListener = new OnClickListener() {
		public void onClick(View v) {
			if (DEBUG) Log.d(getClass().getName(), "Cancel clicked");
			// Destroy with -1 as result. I.e. cancel
			setResult(-1);
			finish();
		}
	};
	
	/**
	 * Handle clicks on the reply button
	 */
	protected OnClickListener onReplyListener = new OnClickListener() {
		public void onClick(View v) {
			if (DEBUG) Log.d(getClass().getName(), ".onClick");
			// Get the reply from the edit box
			EditText replyEdit = (EditText)findViewById(R.id.reply_edit);
			String reply = replyEdit.getText().toString(); 
			// Retrieve the message ID
			long messageId = getIntent().getLongExtra("messageId", 0);
			if (DEBUG) Log.d(getClass().getName(), "Replying to message with ID: " + messageId);
			// Respond with the reply text
			Intent result = new Intent();
			result.putExtra("reply", reply);
			result.putExtra("messageId", messageId);
			setResult(0, result);
			// Shut down the activity
			finish();
		}
	};
}
