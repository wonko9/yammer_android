package com.yammer.v1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class YammerShare extends YammerReplyActivity {

	private CharSequence url = null;	
	
	/**
	 * Activity being created
	 */
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		if (G.DEBUG) Log.d(getClass().getName(), ".onCreate");
		// Listen to the reply button
		Button replyButton = (Button)findViewById(R.id.reply_post);
		replyButton.setOnClickListener(onReplyListener);
		replyButton.setText("Share");
		// Retrieve the intent that started this activity
		Intent intent = getIntent();
		String action = intent.getAction();
		Bundle extras = intent.getExtras();
		if (G.DEBUG) Log.d(getClass().getName(), "Received intent "+action+" ("+intent.getType()+")");
		// Determine content type to get an idea what we should do
		if ( intent.getType().equals("text/plain") ) {
			url = extras.getCharSequence("android.intent.extra.TEXT");
			if (G.DEBUG) Log.d(getClass().getName(), "URL: " + url);
			// Create the default message
			String checkThisOut = getResources().getString(R.string.check_this_out);
			// Dump the url to the edit text view
			EditText shareEdit = (EditText)findViewById(R.id.reply_edit);
			shareEdit.setText(checkThisOut + " " + url);
			shareEdit.setSelection(0, checkThisOut.length());
		}
		// Hide the Yammer logo
		findViewById(R.id.share_logo).setVisibility(View.VISIBLE);
	}
	
	/**
	 * Handle clicks on the send button
	 */
	protected OnClickListener onReplyListener = new OnClickListener() {
		public void onClick(View v) {
			if (G.DEBUG) Log.d(getClass().getName(), ".onClick");
			// Get the reply from the edit box
			EditText replyEdit = (EditText)findViewById(R.id.reply_edit);
			String message = replyEdit.getText().toString();
			// Create an intent containing the message
			Intent intent = new Intent();
			intent.setAction(YammerService.INTENT_POST_MESSAGE);
			intent.putExtra(YammerService.EXTRA_MESSAGE, message);
			// Post the intent to the service
			sendBroadcast(intent);
			// Shut down the activity
			finish();
		}
	};
}
