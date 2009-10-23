/**
 * (C)Copyright 2009 Nullwire
 */

package com.yammer;

import android.app.Activity;
import android.os.Bundle;

public class YammerMessage extends Activity {

	/**
	 * Creating the Yammer message activity
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.message);		
	}
	
}
