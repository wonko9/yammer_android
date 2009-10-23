package com.yammer;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

public class About extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.about);
		// Get version info
		PackageManager pm = getPackageManager();
		PackageInfo pi;
		String versionName = "";
		try {
			pi = pm.getPackageInfo(getPackageName(), 0);
			versionName = pi.versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			versionName = "unknown";
		}
		TextView textView = (TextView)findViewById(R.id.about_text_view);
		textView.setText(String.format(getResources().getString(R.string.about_text), versionName));
	}
}
