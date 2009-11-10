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
import android.graphics.Bitmap;
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
  private static String TAG_TWEETLISTADAPTER = "TWEETLISTADAPTER";

  // Single bitmap loader needed
  private static BitmapDownloader bitmapDownloader = null;
  LayoutInflater layoutInflater = null;
  Context context = null;
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
    if (G.DEBUG) Log.d(TAG_TWEETLISTADAPTER, "TweetListAdapter constructor");
    // Retrieve view inflater
    this.layoutInflater = LayoutInflater.from(context);
    // Create bitmap downloader

    if ( bitmapDownloader == null ) {
      bitmapDownloader = new BitmapDownloader(context);
    }
    // Store context
    this.context = context;
    // prefetch the strings used to build the view
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


  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    //long start = System.currentTimeMillis();

    //if (G.DEBUG) Log.d(TAG_TWEETLISTADAPTER, "TweetListAdapter::getView");
    // It seems we need to call the parent getView - otherwise
    // a crash will occur in AbstractCursor.java:559

    //super.getView(position, convertView, parent);

    c.moveToPosition(position);

    // Did we fetch an old view to be reused - if not, create a new one
    if ( convertView == null ) {
      //if (G.DEBUG) Log.d(TAG_TWEETLISTADAPTER, "A new view was created for tweet");
      // Get text view for tweet to be displayed - assume we can reuse an old one from convertView
      convertView = layoutInflater.inflate(R.layout.list_row, parent, false);
    } else {
      //return convertView;
    }

    // Get the message/tweet text from the database
    String message = c.getString(columnIndex);
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
      String inReplyTo = " in reply to ";
      inReplyToLength = inReplyTo.length();
      replyeeFullNameLength = replyeeFullName.length();
      postPrefix = fullName + inReplyTo + replyeeFullName;
    } else {
      postPrefix = fullName;
    }

    // Colorize the message
    int from = 0, to = 0;
    SpannableString str = SpannableString.valueOf( postPrefix+ ": " + message);	    		
    // Posters full name is white
    from = 0;
    to = fullNameLength;
    str.setSpan(new ForegroundColorSpan(Color.WHITE), from, to, 0);
    // Do we have a replyee
    if ( replyeeFullNameLength > 0 ) {
      from = to;
      to = from + inReplyToLength;
      str.setSpan(new ForegroundColorSpan(Color.GREEN), from, to, 0);
      str.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), from, to, 0);
      from = to;
      to = from + replyeeFullNameLength;
      str.setSpan(new ForegroundColorSpan(Color.WHITE), from, to, 0);
    }		
    // Add 2 due to the appended ": " to the name
    from = to;
    to = from + 2;
    str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, to, 0);
    str.setSpan(new ForegroundColorSpan(Color.WHITE), from, to, 0);
    // Span the rest of the text
    from =  to;
    to = from + message.length();
    str.setSpan(new ForegroundColorSpan(Color.rgb(0x76, 0xd5, 0xff)), from, to, 0); 
    // Get the text view
    TextView tweetTextView = (TextView)convertView.findViewById(R.id.label);
    tweetTextView.setText(str);
    //if (G.DEBUG) Log.d(TAG_TWEETLISTADAPTER, "String: " + str);
    // Get the timestamp column
    long timestamp = c.getLong(createdColumnIndex);
    // Convert the timestamp to a prettier timestamp (e.g. 5 hours ago etc.)
    // Get the ID of the timestamp
    TextView tweetTime = (TextView)convertView.findViewById(R.id.tweet_time);
    tweetTime.setText(prettyDate(timestamp));

    // Download and decode user avatar
    ImageView icon = (ImageView)convertView.findViewById(R.id.user_icon);
    String url = c.getString(mugshotUrlColumnIndex);
    String md5 = c.getString(mugshotMd5ColumnIndex);
    Bitmap bm = bitmapDownloader.getBitmap(url, md5);
    icon.setImageBitmap(bm);

    //if (G.DEBUG) Log.d(TAG_TWEETLISTADAPTER, "Render time ("+position+"): " + (System.currentTimeMillis() - start));

    return convertView;
  }
}
