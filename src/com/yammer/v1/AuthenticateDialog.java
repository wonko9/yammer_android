package com.yammer.v1;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class AuthenticateDialog extends Dialog {

  private static final boolean DEBUG = G.DEBUG;

   public AuthenticateDialog(Context context) {
      super(context);
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      if (DEBUG) Log.d(getClass().getName(), ".onCreate()");
      super.onCreate(savedInstanceState);

      requestWindowFeature(Window.FEATURE_NO_TITLE);
      setContentView(R.layout.authenticate_dialog);

      Button loginButton = (Button)findViewById(R.id.login_button);
      loginButton.setOnClickListener( new View.OnClickListener() {
         public void onClick(View _view) {
            if (DEBUG) Log.d(getClass().getName(), ".onClick()");
            login((Button)_view);
         }
      });
   }

   private Context getApplicationContext() {
     return getContext().getApplicationContext();
   }

   protected void login(Button _button) {
     int code = YammerProxy.getYammerProxy(getApplicationContext()).login(getEmail(), getPassword());
     if(200 == code) {
       dismiss();
     } else {
       setErrorText(code);
     }
   }

   private void setErrorText(int _code) {
     int resId;
    switch(_code) {
      case 400:
      case 401:
        resId = R.string.auth_error_401_text;
        break;
      case 403:
        resId = R.string.auth_error_403_text;
        break;
      default:
        resId = R.string.auth_error_500_text;
    }
    TextView text = (TextView)findViewById(R.id.welcome_text);
    text.setText(resId);
    text.setTextColor(R.style.notice);
   }

   private String getEmail() {
     return ((TextView)findViewById(R.id.email_field)).getText().toString();
   }

  private String getPassword() {
    return ((TextView)findViewById(R.id.password_field)).getText().toString();
  }

}
