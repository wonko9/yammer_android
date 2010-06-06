package com.yammer.v1;

import com.yammer.v1.models.Message;
import com.yammer.v1.models.Network;
import com.yammer.v1.models.User;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.yammer.v1.settings.SettingsEditor;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class TweetListAdapter extends SimpleCursorAdapter {

  private static final boolean DEBUG = G.DEBUG;

  // Single bitmap loader needed
  private static BitmapDownloader bitmapDownloader = null;
  LayoutInflater layoutInflater = null;
  Context mContext = null;

  String in_reply_to;
  String a_moment_ago;
  String one_minute_ago;
  String a_few_minutes_ago;
  String minutes_ago;
  String about_half_an_hour_ago;
  String about_an_hour_ago;
  String hours_ago;
  String days_ago;


  int messageIdIndex;
  int columnIndex;
  int fullNameColumnIndex;
  int emailColumnIndex;
  int replyeeEmailColumnIndex;
  int replyeeColumnIndex;
  int createdColumnIndex;
  int mugshotUrlColumnIndex;
  int mugshotMd5ColumnIndex;

  SimpleDateFormat formatter;

  public TweetListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
    super(context, layout, c, from, to);
    if (DEBUG) Log.d(getClass().getName(), ".constructor");
    this.mContext = context;

    ensureLayoutInflater();
    ensureBitmapDownloader();
    ensureTimestampFormatter();
    prefetchStrings(context);
    cacheColumnIndices();
  }

  private void ensureTimestampFormatter() {
    formatter = new SimpleDateFormat("MMMMM dd yyyy, HH:mm:ss");
  }

  private void ensureLayoutInflater() {
    layoutInflater = LayoutInflater.from(mContext);
  }

  private void cacheColumnIndices() {
    Cursor c = getCursor();
    messageIdIndex = c.getColumnIndex(Message.FIELD_MESSAGE_ID);
    columnIndex = c.getColumnIndex(Message.FIELD_MESSAGE);
    createdColumnIndex = c.getColumnIndex(Message.FIELD_TIMESTAMP);
    emailColumnIndex = c.getColumnIndex(User.FIELD_EMAIL);
    replyeeEmailColumnIndex = c.getColumnIndex(User.FIELD_REPLYEE_EMAIL);
    fullNameColumnIndex = c.getColumnIndex(User.FIELD_FULL_NAME);
    replyeeColumnIndex = c.getColumnIndex(User.FIELD_REPLYEE_FULL_NAME);
    mugshotUrlColumnIndex = c.getColumnIndex(User.FIELD_MUGSHOT_URL);		
    mugshotMd5ColumnIndex = c.getColumnIndex(User.FIELD_MUGSHOT_MD5);
  }

  private void prefetchStrings(Context context) {
    in_reply_to = " " + context.getResources().getString(R.string.in_reply_to) + " ";
    a_moment_ago = context.getResources().getString(R.string.a_moment_ago);
    one_minute_ago = context.getResources().getString(R.string.one_minute_ago);
    a_few_minutes_ago = context.getResources().getString(R.string.a_few_minutes_ago);
    minutes_ago = context.getResources().getString(R.string.minutes_ago);
    about_half_an_hour_ago = context.getResources().getString(R.string.about_half_an_hour_ago);
    about_an_hour_ago = context.getResources().getString(R.string.about_an_hour_ago);
    hours_ago = context.getResources().getString(R.string.hours_ago);
    days_ago = context.getResources().getString(R.string.days_ago);
  }

  private void ensureBitmapDownloader() {
    if ( bitmapDownloader == null ) {
      bitmapDownloader = new BitmapDownloader(mContext);
    }
  }

  /**
   * Timestamp created_at will be in this format: "2009/01/04 21:57:17 +0000"
   * @param timestamp
   * @return
   */
  private String prettyDate(long timestamp) {
    String prettyDate = null;
    // Get the number of seconds ago since this message was posted
    long seconds = ( System.currentTimeMillis() - timestamp ) / 1000;
    // Convert it to a prettier date
    if ( seconds <= 30 ) {
      prettyDate = a_moment_ago;
    } else if ( seconds > 30 && seconds <= 90 ) {
      prettyDate = one_minute_ago;			
    } else if ( seconds > 90 && seconds <= 60*5 ) {
      prettyDate = a_few_minutes_ago;
    } else if ( seconds > 60*5 && seconds <= 60*25 ) {
      prettyDate = seconds/60 + " " + minutes_ago;
    } else if ( seconds > 60*25 && seconds <= 60*35 ) {
      prettyDate = about_half_an_hour_ago;
    } else if ( seconds > 60*35 && seconds <= 60*55 ) {
      prettyDate = seconds/60 + " " + minutes_ago;
    } else if ( seconds > 60*55 && seconds <= 60*65 ) {
      prettyDate = about_an_hour_ago;
    } else if ( seconds > 60*65 && seconds <= 60*60*24 ) {
      prettyDate = seconds/60/60 + " " + hours_ago;
    } else if ( seconds > 60*60*24 && seconds <= 60*60*24 ) {
      prettyDate = seconds/60/60 + " " + hours_ago;
    } else if ( seconds > 60*60*24 && seconds <= 7*60*60*24 ) {
      prettyDate = seconds/60/60/24 + " " + days_ago;
    } else {
      prettyDate = formatter.format(new Date(timestamp));
    }

    return prettyDate;
  }

  static class ViewHolder {
    ImageView user_icon;
    TextView  message;
    TextView  tweet_time;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;

    if ( convertView == null ) {
      convertView = layoutInflater.inflate(R.layout.list_row, parent, false);

      holder = new ViewHolder();
      holder.user_icon = (ImageView)convertView.findViewById(R.id.user_icon);
      holder.message = (TextView)convertView.findViewById(R.id.message);
      holder.tweet_time = (TextView)convertView.findViewById(R.id.tweet_time);

      convertView.setTag(holder);

    } else {
      holder = (ViewHolder)convertView.getTag();
    }

    Cursor cursor = getCursor();
    
    cursor.moveToPosition(position);

    // Get full name of poster
    String fullName = cursor.getString(fullNameColumnIndex);

    // Full name not found?
    if ( fullName == null ) {
      // Use email for full name
      fullName = cursor.getString(emailColumnIndex);
    }
    // Full name still empty
    if ( fullName == null ) {
      // Assume name is unknown
      fullName = mContext.getResources().getString(R.string.unknown);
    }

    // Get full name of replyee
    String replyeeFullName = cursor.getString(replyeeColumnIndex);

    // Convert full name to first name only
    if ("firstname".equals(getSettings().getDisplayName())) {
      if (0 < fullName.indexOf(' ')) {
        // Display firstname - i.e. text up until first space or EOF$
        fullName = fullName.substring(0, fullName.indexOf(' '));
      }
      if (null != replyeeFullName && 0 < replyeeFullName.indexOf(' ')) {
        replyeeFullName = replyeeFullName.substring(0, replyeeFullName.indexOf(' '));				
      }
    } else if ("email_only".equals(getSettings().getDisplayName())) {
      fullName = cursor.getString(emailColumnIndex);
      if (replyeeFullName != null ) {
        replyeeFullName = cursor.getString(replyeeEmailColumnIndex);
      }
    }

    // Do we have a replyee
    int fullNameLength = fullName.length();			
    int inReplyToLength = 0;
    int replyeeFullNameLength = 0;

    String postPrefix = "";
    if ( replyeeFullName != null  ) {
      inReplyToLength = in_reply_to.length();
      replyeeFullNameLength = replyeeFullName.length();
      postPrefix = fullName + in_reply_to + replyeeFullName;
    } else {
      postPrefix = fullName;
    }

    // Get the message/tweet text from the database
    String message = cursor.getString(columnIndex);

    // Colorize the message
    int from = 0, to = 0;
    SpannableString str = SpannableString.valueOf(postPrefix+ ": " + message);
    
    // Posters full name is white
    from = 0;
    to = fullNameLength;
    str.setSpan(new ForegroundColorSpan(Color.BLACK), from, to, 0);

    // Do we have a replyee
    if ( replyeeFullNameLength > 0 ) {
      from = to;
      to = from + inReplyToLength;
      str.setSpan(new ForegroundColorSpan(Color.rgb(0x4c, 0x4c, 0x4c)), from, to, 0);
      from = to;
      to = from + replyeeFullNameLength;
      str.setSpan(new ForegroundColorSpan(Color.BLACK), from, to, 0);
    }

    // Add 2 due to the appended ": " to the name
    from = to;
    to = from + 2;
    str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, to, 0);
    str.setSpan(new ForegroundColorSpan(Color.BLACK), from, to, 0);

    // Span the rest of the text
    from =  to;
    to = from + message.length();
    str.setSpan(new ForegroundColorSpan(Color.BLACK), from, to, 0);

    // Set the message
    holder.message.setText(str);

    // Convert the timestamp to a prettier timestamp (e.g. 5 hours ago etc.)
    holder.tweet_time.setText(prettyDate(cursor.getLong(createdColumnIndex)));

    // Download and decode avatar
    holder.user_icon.setImageBitmap(
        bitmapDownloader.getBitmap(cursor.getString(mugshotUrlColumnIndex), cursor.getString(mugshotMd5ColumnIndex))
    );
   
    return convertView;
  }

  SettingsEditor settings;
  private SettingsEditor getSettings() {
    if(null == this.settings) {
      this.settings = new SettingsEditor(mContext);
    }
    return this.settings;
  }
}
