package com.yammer;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;

public class AuthenticateDialog extends Dialog {
	
	private static String TAG_AUTHENTICATEDIALOG = "AuthenticateDialog";	
	Context context = null;
	
	public AuthenticateDialog(Context context) {
		super(context);
		this.context = context;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (G.DEBUG) Log.d(TAG_AUTHENTICATEDIALOG, "AuthenticateDialog::onCreate");
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.authenticate_dialog);
		Button okButton = (Button)this.findViewById(R.id.ok_button);
		okButton.setOnClickListener( new View.OnClickListener() {
			public void onClick(View arg0) {
				if (G.DEBUG) Log.d(TAG_AUTHENTICATEDIALOG, "Dismissing authenticate dialog");
				// Initiate authorization means that the service will start retrieving
				// tokens and so on. Service will also send intents to start the browser.
				Intent intent = new Intent( "com.yammer:AUTHORIZATION_INITIATE" );
				context.sendBroadcast(intent);
			}
		});
	}
}
