package com.yammer.v1;
import com.yammer.v1.settings.SettingsEditor;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class Browser extends Activity {
  
  private static final boolean DEBUG = G.DEBUG;
  
  private static WebView webView = null;
  
  private static final String CALLBACK_TOKEN = "oauth_verifier=";
  
  SettingsEditor settings;
  private SettingsEditor getSettings() {
    if(null == this.settings) {
      this.settings = new SettingsEditor(getApplicationContext());
    }
    return this.settings;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (DEBUG) Log.d(getClass().getName(), ".onCreate:"+this);
    setContentView(R.layout.browser);

    // Create webview	
    webView = (WebView) findViewById(R.id.web_view);        	
    // webView.getUrl()
    if (DEBUG) Log.d(getClass().getName(), ".getUrl(): " + webView.getUrl());        
    // Enable Java script
    webView.getSettings().setJavaScriptEnabled(true);
    
    if(null != OAuthCustom.HTTP_BASIC_ID) {
      webView.setHttpAuthUsernamePassword(
          OAuthCustom.HTTP_BASIC_HOST, 
          OAuthCustom.HTTP_BASIC_REALM, 
          OAuthCustom.HTTP_BASIC_ID, 
          OAuthCustom.HTTP_BASIC_PASSWORD
      );
    }

    // Attach web view client to web view
    webView.setWebViewClient( new WebViewClient() { 

      //        	@Override
      //        	public void onLoadResource(WebView view, String Url) {
      //        		if (DEBUG) Log.d("BROWSER", "Loading: " + Url);
      //        		if ( Url.startsWith(YammerSettings.getUrl(getApplicationContext()) + "/") ) {
      //        			if (DEBUG) Log.d(getClass().getName(), "Seems OK. Trying to shut down WebView");
      //        		}
      //        	}

      /**
       * Page started loading
       */
      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (DEBUG) Log.d(getClass().getName(), "Page started loading: " + url);
        try {
          showDialog(ID_DIALOG_LOADING);
        } catch( Exception e ) {
          e.printStackTrace();
        }  
        if ( url.equals(getSettings().getUrl() + "/users/new") ) {
          if (DEBUG) Log.d(getClass().getName(), "REDIRECTING user to: /login");        			
          webView.loadUrl(getSettings().getUrl() + "/login");
          
        } else if ( 0 < url.indexOf(CALLBACK_TOKEN) ) {
          if (DEBUG) Log.d(getClass().getName(), "Seems OK. Trying to shut down WebView");
          int callbackTokenIndex = url.indexOf(CALLBACK_TOKEN) + CALLBACK_TOKEN.length();	
          String callbackToken = url.substring(callbackTokenIndex, callbackTokenIndex + 4);
          // Extract the 4 digit callback token
          if (DEBUG) Log.d(getClass().getName(), "callback_token: " + callbackToken);
          
          Intent intent = new Intent(YammerService.INTENT_AUTHENTICATION_COMPLETE);
          intent.putExtra(YammerService.EXTRA_TOKEN, callbackToken);
          sendBroadcast(intent);
          
          finish();
          // We will handle this ourselves - don't do anything
          // All okay, so set result = 0 - i.e. not canceled
          setResult(0);
          
        } else if (DEBUG) {
          Log.d(getClass().getName(), "Unknown redirect: " + url);
        }
      }

      /**
       * Page has finished loading
       */
      @Override
      public void onPageFinished(WebView view, String url) {
        if (DEBUG) Log.d(getClass().getName(), "Page finished loading: " + url);
        try{
          if (DEBUG) Log.d(getClass().getName(), "Removing loading dialog");
          removeDialog(ID_DIALOG_LOADING);
        } catch( Exception e ) {
          e.printStackTrace();
        }
      }

      /**
       * A network error occured
       */
      @Override
      public void onReceivedError(WebView _view, int _errorCode, String _desc, String _url) {
        if (DEBUG) {
          Toast.makeText(getApplicationContext(), "Network Error: "+_desc+" "+_url, Toast.LENGTH_LONG).show();
        }
        //        	  try {
        //              Thread.sleep(30);
        //            } catch (InterruptedException e) {
        //              // TODO Auto-generated catch block
        //              e.printStackTrace();
        //            }
        // Send an intent to the Yammer activity notifying about the error
        sendBroadcast(new Intent( "com.yammer.v1:NETWORK_ERROR_FATAL" ));
        // Finish the browser activity
        finish();
      }
    } );

    if (DEBUG) Log.d(getClass().getName(), "onCreate::savedInstanceState: " + savedInstanceState);

    if ( savedInstanceState != null && savedInstanceState.containsKey("index") ) {
      // Activity was probably recycled, so load previous state
      webView.restoreState(savedInstanceState);
    } else {
      // Get the extras from the intent
      Intent intent = getIntent();
      String responseUrl = intent.getExtras().getString("responseUrl");
      if (DEBUG) Log.d(getClass().getName(), "No state saved, so loading URL: " + responseUrl);
      // Load the response URL
      webView.loadUrl(responseUrl);
    }
    // Cancel by default
    setResult(-1);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (DEBUG) Log.d(getClass().getName(), "Browser::onStart");
  }

  // Dialog ID's
  private static int ID_DIALOG_LOADING = 0;

  @Override
  protected Dialog onCreateDialog(int id) {
    if ( id == ID_DIALOG_LOADING ) {
      ProgressDialog dialog = new ProgressDialog(this);
      dialog.setMessage(getResources().getString(R.string.loading));
      dialog.setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface arg0) {
          if (DEBUG) Log.d(getClass().getName(), "Canceling loading dialog");
          // if canceled, then finish this activity
          finish();
        }
      });			
      return dialog;
    }
    return super.onCreateDialog(id);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    if (DEBUG) Log.d(getClass().getName(), "Browser::onRestoreInstanceState: " + savedInstanceState);
    webView.restoreState(savedInstanceState);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    if (DEBUG) Log.d(getClass().getName(), "Browser::onSaveInstanceState");
    // If no page was loaded, it means the state isn't usable,
    // don't save the state and let the browser start all over again
    // once onCreate is called again.
    if ( webView.getUrl() != null ) {
      if (DEBUG) Log.d(getClass().getName(), "Saving browser state");
      webView.saveState(state);			
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (DEBUG) Log.d(getClass().getName(), "Browser::onPause");
  }

  @Override
  public void onStop() {
    super.onStop();
    if (DEBUG) Log.d(getClass().getName(), "webView.getUrl(): " + webView.getUrl());        
    //webView.stopLoading();
    if (DEBUG) Log.d(getClass().getName(), "Browser::onStop");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (DEBUG) Log.d(getClass().getName(), "Browser::onDestroy");
  }
}
