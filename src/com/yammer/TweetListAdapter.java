package com.yammer;

import static com.yammer.YammerDataConstants.EMAIL;

import static com.yammer.YammerDataConstants.FULL_NAME;
import static com.yammer.YammerDataConstants.MESSAGE;
import static com.yammer.YammerDataConstants.MUGSHOT_MD5;
import static com.yammer.YammerDataConstants.MUGSHOT_URL;
import static com.yammer.YammerDataConstants.REPLYEE_EMAIL;
import static com.yammer.YammerDataConstants.REPLYEE_FULL_NAME;
import static com.yammer.YammerDataConstants.TIMESTAMP;

import java.text.SimpleDateFormat;
import java.util.Date;

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

  private static String TAG_TWEETLISTADAPTER = "TWEETLISTADAPTER";

  // Single bitmap loader needed
  private static BitmapDownloader bitmapDownloader = null;
  LayoutInflater layoutInflater = null;
  Context context = null;

  final String in_reply_to;
  final String a_moment_ago;
  final String one_minute_ago;
  final String a_few_minutes_ago;
  final String minutes_ago;
  final String about_half_an_hour_ago;
  final String about_an_hour_ago;
  final String hours_ago;
  final String days_ago;

  final int columnIndex;
  final int fullNameColumnIndex;
  final int emailColumnIndex;
  final int replyeeEmailColumnIndex;
  final int replyeeColumnIndex;
  final int createdColumnIndex;
  final int mugshotUrlColumnIndex;
  final int mugshotMd5ColumnIndex;

  Cursor c;

  final SimpleDateFormat formatter;

  public TweetListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
    super(context, layout, c, from, to);
    if (DEBUG) Log.d(TAG_TWEETLISTADAPTER, "TweetListAdapter constructor");
    // Retrieve view inflater
    this.layoutInflater = LayoutInflater.from(context);
    // Create bitmap downloader

    if ( bitmapDownloader == null ) {
      bitmapDownloader = new BitmapDownloader(context);
    }
    // Store context
    this.context = context;

    // prefetch the strings used to build the view
    in_reply_to = " " + context.getResources().getString(R.string.in_reply_to) + " ";
    a_moment_ago = context.getResources().getString(R.string.a_moment_ago);
    one_minute_ago = context.getResources().getString(R.string.one_minute_ago);
    a_few_minutes_ago = context.getResources().getString(R.string.a_few_minutes_ago);
    minutes_ago = context.getResources().getString(R.string.minutes_ago);
    about_half_an_hour_ago = context.getResources().getString(R.string.about_half_an_hour_ago);
    about_an_hour_ago = context.getResources().getString(R.string.about_an_hour_ago);
    hours_ago = context.getResources().getString(R.string.hours_ago);
    days_ago = context.getResources().getString(R.string.days_ago);
    // Prefetch column indexes
    columnIndex = c.getColumnIndex(MESSAGE);
    emailColumnIndex = c.getColumnIndex(EMAIL);
    replyeeEmailColumnIndex = c.getColumnIndex(REPLYEE_EMAIL);
    fullNameColumnIndex = c.getColumnIndex(FULL_NAME);
    replyeeColumnIndex = c.getColumnIndex(REPLYEE_FULL_NAME);
    createdColumnIndex = c.getColumnIndex(TIMESTAMP);
    mugshotUrlColumnIndex = c.getColumnIndex(MUGSHOT_URL);		
    mugshotMd5ColumnIndex = c.getColumnIndex(MUGSHOT_MD5);		
    // Prefetch cursor
    this.c = c;
    // Prefetch formatter
    formatter = new SimpleDateFormat("MMMMM dd yyyy, HH:mm:ss");

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

    c.moveToPosition(position);

    // Get full name of poster
    String fullName = c.getString(fullNameColumnIndex);

    // Full name not found?
    if ( fullName == null ) {
      // Use email for full name
      fullName = c.getString(emailColumnIndex);
    }
    // Full name still empty
    if ( fullName == null ) {
      // Assume name is unknown
      fullName = context.getResources().getString(R.string.unknown);
    }

    // Get full name of replyee
    String replyeeFullName = c.getString(replyeeColumnIndex);

    // Convert full name to first name only
    if (YammerSettings.getDisplayName(this.context).equals("firstname")) {
      if (0 < fullName.indexOf(' ')) {
        // Display firstname - i.e. text up until first space or EOF$
        fullName = fullName.substring(0, fullName.indexOf(' '));
      }
      if (null != replyeeFullName && 0 < replyeeFullName.indexOf(' ')) {
        replyeeFullName = replyeeFullName.substring(0, replyeeFullName.indexOf(' '));				
      }
    } else if (YammerSettings.getDisplayName(this.context).equals("email_only")) {
      fullName = c.getString(emailColumnIndex);
      if (replyeeFullName != null ) {
        replyeeFullName = c.getString(replyeeEmailColumnIndex);
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
    String message = c.getString(columnIndex);

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
    holder.tweet_time.setText(prettyDate(c.getLong(createdColumnIndex)));

    // Download and decode avatar
    holder.user_icon.setImageBitmap(
        bitmapDownloader.getBitmap(c.getString(mugshotUrlColumnIndex), c.getString(mugshotMd5ColumnIndex))
    );

    return convertView;
  }
}
