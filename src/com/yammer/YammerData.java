package com.yammer;

import com.yammer.YammerDataConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class YammerData extends SQLiteOpenHelper implements YammerDataConstants {

  private static final boolean DEBUG = G.DEBUG;

  public static class YammerDataException extends Exception {
    private static final long serialVersionUID = -3367954627916549416L;
    public YammerDataException(Exception _cause) {
      super();
      initCause(_cause);
    }
  }

  private static final String TAG_YDATABASE = "YammerDB";
  private static final String DATABASE_NAME = "yammer.db";
  private static final int DATABASE_VERSION = 17;

  public YammerData(Context ctx) {
    super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public void onCreate(SQLiteDatabase db) {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.onCreate");

    // Create the needed tables
    if (DEBUG) Log.d(TAG_YDATABASE, "Creating table: " + TABLE_USERS);
    db.execSQL(	"CREATE TABLE " + TABLE_MESSAGES +" ("
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " 
        + TIMESTAMP + " INTEGER, " 
        + MESSAGE + " TEXT NOT NULL, "
        + SENDER_ID + " BIGINT, "
        + MESSAGE_ID + " BIGINT UNIQUE NOT NULL, "
        + SENDER_TYPE + " TEXT, "
        + THREAD_ID + " BIGINT, "
        + CLIENT_TYPE + " TEXT, "
        + REPLIED_TO_ID + " BIGINT, "
        + CREATED_AT + " TEXT, "
        + NETWORK_ID + " BIGINT, "
        + DELETED + " BOOLEAN DEFAULT 0"
        + ");"
    );

    if (DEBUG) Log.d(TAG_YDATABASE, "Creating table: " + TABLE_USERS);
    db.execSQL(	"CREATE TABLE " + TABLE_USERS +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + USER_ID + " BIGINT UNIQUE NOT NULL, "
        + MUGSHOT_URL + " TEXT, "
        + MUGSHOT_MD5 + " TEXT, "
        + FULL_NAME + " TEXT, "
        + NAME + " TEXT, "
        + TITLE + " TEXT, "
        + EMAIL + " TEXT, "
        + FIELD_USERS_URL + " TEXT, "
        + IS_FOLLOWING + " BOOLEAN DEFAULT 0, "
        + WEB_URL + " TEXT"
        + ");"
    );

    if (DEBUG) Log.d(TAG_YDATABASE, "Creating table: " + TABLE_NETWORKS);
    db.execSQL(	"CREATE TABLE " + TABLE_NETWORKS +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + NETWORK_ID + " BIGINT NOT NULL, "
        + USER_ID + " BIGINT UNIQUE NOT NULL, "
        + ACCESS_TOKEN + " TEXT, "
        + ACCESS_TOKEN_SECRET + " TEXT, "
        + LAST_MESSAGE_ID + " BIGINT"
        + ");"
    );

    if (G.DEBUG) Log.d(TAG_YDATABASE, "Creating table:" + TABLE_URLS);
    db.execSQL("CREATE TABLE " + TABLE_URLS +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_URLS_MESSAGE_ID + " BIGINT NOT NULL, "
        + FIELD_URLS_NETWORK_ID + " BIGINT NOT NULL, "
        + FIELD_URLS_URL + " TEXT, "
        + FIELD_URLS_TITLE + " TEXT, "
        + FIELD_URLS_FAVICON_ID + " TEXT"
        + ");"
    );
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (DEBUG) Log.i(TAG_YDATABASE, "YammerData.onUpgrade");

    // Drop all tables
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_NETWORKS);
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_URLS);
    // Recreate all tables
    onCreate(db);
  }

  public void clearMessages() {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.resetData");
    SQLiteDatabase db = this.getWritableDatabase();
    db.execSQL("DELETE FROM " + TABLE_MESSAGES);
    clearFeeds();
  }
  
  public void resetData(long networkId) {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.resetData for network");
    SQLiteDatabase db = this.getWritableDatabase();
    db.execSQL("DELETE FROM " + TABLE_MESSAGES + " WHERE " + NETWORK_ID + "=" + networkId);
    db.execSQL("DELETE FROM " + TABLE_NETWORKS + " WHERE " + NETWORK_ID + "=" + networkId);
    clearFeeds();
  }

  public boolean isMessageFromUser(long messageId, long userId) {
    Cursor c = null;
    try {
      // Update the database
      SQLiteDatabase db = this.getReadableDatabase();
      // Just try to see if the message with given ID returns any messages if matched with given user ID
      c = db.query(TABLE_MESSAGES, new String[] {}, USER_ID+"="+userId+" AND "+MESSAGE_ID+"="+messageId, null, null, null, null);
      if ( c.getCount() > 0 ) {
        // Yep, seems the message was from the given user
        return true;
      }
    } catch (Exception e) {
      if (DEBUG) Log.d(TAG_YDATABASE, "Whoops.. isMessageFromUser failed: " + e.getMessage());
      e.printStackTrace();
    } finally {
      c.close();
    }
    return false;
  }

  public static int ID_TARGET_USER = 0;
  public static int ID_TARGET_TAG = 1;

  public void subscribe(int target, long subscriptionId, boolean subscribe) {
    if ( target == ID_TARGET_USER ) {
      // subscribe or unsubscribe user
      SQLiteDatabase db = this.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put(IS_FOLLOWING, subscribe);
      int count = db.update(TABLE_USERS, values, USER_ID + "=" + subscriptionId, null);			
      if (G.DEBUG) Log.d(TAG_YDATABASE, "Number of user updated: " + count);
    } else if ( target == ID_TARGET_TAG ) {
      // Not supported
    }
  }

  public void subscribeReset(long networkId) {
    // subscribe or unsubscribe user
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(IS_FOLLOWING, false);
    int count = db.update(TABLE_USERS, values, null, null);			
    if (G.DEBUG) Log.d(TAG_YDATABASE, "Number of user updated: " + count);
  }

  public void createNetwork(long networkId, long userId, String accessToken, String accessTokenSecret) {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.createNetwork");
    try {
      // Update the database
      SQLiteDatabase db = this.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put(NETWORK_ID, networkId);
      values.put(USER_ID, userId);
      values.put(ACCESS_TOKEN, accessToken);
      values.put(ACCESS_TOKEN_SECRET, accessTokenSecret);
      values.put(LAST_MESSAGE_ID, 0);
      // Insert into database
      db.insertOrThrow(TABLE_NETWORKS, null, values);
      // Remove all messages from the network
      db.delete(TABLE_MESSAGES, NETWORK_ID + "=" + networkId, null);
    } catch (SQLiteConstraintException e) {
      e.printStackTrace();
      if (DEBUG) Log.d(TAG_YDATABASE, "SQLiteConstraintException - network already exists probably, so just updating it");
      // Network already exists probably, so just update it
      updateAccessTokens(networkId, accessToken, accessTokenSecret);
    }
  }

  /**
   * Get access token for given network ID 
   * @param networkId
   * @return
   */
  public String getAccessToken(long networkId, long userId) {
    String accessToken = null;
    Cursor c = null;
    try {
      // Update the database
      SQLiteDatabase db = this.getReadableDatabase();
      c = db.query(TABLE_NETWORKS, new String[] {ACCESS_TOKEN}, NETWORK_ID+"="+networkId+" AND "+USER_ID+"="+userId, null, null, null, null);
      c.moveToFirst();
      int columnIndex = c.getColumnIndex(ACCESS_TOKEN);
      accessToken = c.getString(columnIndex);
      c.close();
    } catch (Exception e) {
      if (DEBUG) Log.d(TAG_YDATABASE, "It seems an access token could not be found - returning null");
    } finally {
      c.close();
    }
    return accessToken;
  }

  /**
   * getAccessTokenSecret
   * @param networkId
   * @return
   */
  public String getAccessTokenSecret(long networkId, long userId) {
    String accessTokenSecret = null;
    Cursor c = null;
    try {
      // Update the database
      SQLiteDatabase db = this.getReadableDatabase();
      c = db.query(TABLE_NETWORKS, new String[] {ACCESS_TOKEN_SECRET}, NETWORK_ID+"="+networkId+" AND "+USER_ID+"="+userId, null, null, null, null);
      c.moveToFirst();
      int columnIndex = c.getColumnIndex(ACCESS_TOKEN_SECRET);
      accessTokenSecret = c.getString(columnIndex);
    } catch (Exception e) {
      if (DEBUG) Log.d(TAG_YDATABASE, "It seems an access token could not be found - returning null");
    } finally {
      c.close();
    }
    return accessTokenSecret;
  }

  public void updateAccessTokens(long networkId, String accessToken, String accessTokenSecret) {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.updateAccessTokens");
    // Update the database
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(ACCESS_TOKEN, accessToken);
    values.put(ACCESS_TOKEN_SECRET, accessTokenSecret);
    int count = db.update(TABLE_NETWORKS, values, NETWORK_ID + "=" + networkId, null);
    if ( count != 1 ) {
      if (DEBUG) Log.w(TAG_YDATABASE, "Problem updating network state. Count: " + count);
    }		
  }

  /**
   * Get the highest message ID in the messages table
   * @param networkId
   * @return
   */
  public long getLastMessageId(long networkId) {
    long messageId = 0;
    Cursor c = null;
    try {
      // Update the database
      SQLiteDatabase db = this.getReadableDatabase();
      c = db.query(TABLE_NETWORKS, new String[] {LAST_MESSAGE_ID}, NETWORK_ID+"="+networkId, null, null, null, null);
      c.moveToFirst();
      int columnIndex = c.getColumnIndex(LAST_MESSAGE_ID);
      messageId = c.getLong(columnIndex);
    } catch (Exception e) {
      e.printStackTrace();
      if (DEBUG) Log.d(TAG_YDATABASE, "It seems last message ID could not be found - returning 0");
    } finally {
      c.close();			
    }
    return messageId;
  }	

  /**
   * Update the last message ID for given network
   * @param networkId
   * @param messageId
   */
  public void updateLastMessageId(long networkId, long messageId) {
    if (DEBUG) Log.d(TAG_YDATABASE, "YammerData.updateLastMessageId");
    // Update the database
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(LAST_MESSAGE_ID, messageId);
    int count = db.update(TABLE_NETWORKS, values, NETWORK_ID + "=" + networkId, null);
    if ( count != 1 ) {
      if (DEBUG) Log.w(TAG_YDATABASE, "Problem updating network state. Count: " + count);
    }
  }

  public long addMessage(long networkId, JSONObject messageReference) throws Exception {
    // Try to get the parsed message
    try {
      // Extract relevant strings from JSON object
      JSONObject messageBody = messageReference.getJSONObject("body");
      String plainMessage = messageBody.getString("plain");
      long messageId = messageReference.getLong("id");
      String senderId = messageReference.getString("sender_id");
      String senderType = messageReference.getString("sender_type");
      String threadId = messageReference.getString("thread_id");
      String clientType = messageReference.getString("client_type");
      String repliedToId = messageReference.getString("replied_to_id");
      String createdAt = messageReference.getString("created_at");
      if (DEBUG) Log.d(TAG_YDATABASE, "Added message_id: " + messageId);
      // Update the database
      SQLiteDatabase db = this.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put(MESSAGE, plainMessage);
      values.put(MESSAGE_ID, messageId);
      values.put(SENDER_ID, senderId);
      values.put(SENDER_TYPE, senderType);
      values.put(THREAD_ID, threadId);
      values.put(CLIENT_TYPE, clientType);
      values.put(REPLIED_TO_ID, repliedToId);
      values.put(NETWORK_ID, networkId);
      values.put(CREATED_AT, createdAt);
      // Convert the timestamp to UNIX time
      long timestamp = System.currentTimeMillis();
      // Create a DateFormat object to work on
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss ZZZZZ");
      try {
        timestamp = dateFormat.parse(createdAt).getTime();
      } catch (ParseException e) {
        if (DEBUG) Log.w(TAG_YDATABASE, "Could not parse date");
      }			
      values.put(TIMESTAMP, timestamp);
      // Insert into database
      db.insertOrThrow(TABLE_MESSAGES, null, values);
      try {
        // Fetch any URL's embedded in the message
        JSONArray jsonArray = messageBody.getJSONArray("urls");
        if (DEBUG) Log.d(TAG_YDATABASE, "jsonArray.length(): " + jsonArray.length());
        // Add all fetched messages tp the database
        for( int i=0; i < jsonArray.length(); i++ ) {
          String url = jsonArray.getString(i);
          if (DEBUG) Log.d(TAG_YDATABASE, "Extracted URL: " + url);
          // Add the URL to the database
          addUrl(messageId, networkId, url);
        }
      } catch( Exception e ) {
        if (DEBUG) Log.d(TAG_YDATABASE, "No URL's found");
        //e.printStackTrace();
      }
      // Return the ID of the message stored
      return messageId;
    } catch (JSONException e) {
      e.printStackTrace();
      // Message wasn't added
      throw new Exception("Could not add message to database (JSONException): " + 
          messageReference.toString());
      // No message stored, so return -1
    } catch (SQLiteConstraintException e) {
      // Could not add the message
      throw new Exception("Could not add message to database (SQLiteConstraintException): " + 
          messageReference.toString());
    }
  }

  public void addUser(JSONObject userReference) throws Exception {
    try {
      // Extract relevant strings from JSON object
      long userId = userReference.getLong("id");
      String mugshotUrl = userReference.getString("mugshot_url");
      String mugshotMd5 = MD5Gen.md5(mugshotUrl);
      String fullName = userReference.getString("full_name");
      String name = userReference.getString("name");
      // TODO: Extract primary email
      String email = "email@domain.com";
      String webUrl = userReference.getString("web_url");
      String jobTitle = userReference.getString("job_title");
      String url = userReference.getString("url");
      // Prepare to throw the data into the database
      ContentValues values = new ContentValues();			
      values.put(USER_ID, userId);
      values.put(MUGSHOT_URL, mugshotUrl);
      values.put(MUGSHOT_MD5, mugshotMd5);
      values.put(FULL_NAME, fullName);
      values.put(NAME, name);
      values.put(WEB_URL, webUrl);
      values.put(EMAIL, email);
      values.put(TITLE, jobTitle);
      values.put(FIELD_USERS_URL, url);
      // Insert into database
      SQLiteDatabase db = this.getWritableDatabase();
      db.insertOrThrow(TABLE_USERS, null, values);			
      if (DEBUG) Log.d(TAG_YDATABASE, "Added user_id: " + userId);
    } catch (JSONException e) {
      e.printStackTrace();
      // Message wasn't added
      throw new Exception("Could not add message to database (JSONException): " + 
          userReference.toString());
      // No message stored, so return -1
    } catch (SQLiteConstraintException e) {
      // Could not add the message
      throw new Exception("Could not add message to database (SQLiteConstraintException): " + 
          userReference.toString());
    }		
  }

  public void addUrl(long messageId, long networkId, String url) {
    ContentValues values = new ContentValues();                        
    values.put(FIELD_URLS_MESSAGE_ID, messageId);
    values.put(FIELD_URLS_NETWORK_ID, networkId);
    values.put(FIELD_URLS_URL, url);
    getWritableDatabase().insertOrThrow(TABLE_URLS, null, values);                
  }

}
