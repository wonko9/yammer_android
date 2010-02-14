package com.yammer.v1.models;

import com.yammer.v1.G;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class Network extends Base {

  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "networks";
  
  public static final String FIELD_NETWORK_ID = "network_id";
  public static final String FIELD_USER_ID = User.FIELD_USER_ID;
  public static final String FIELD_ACCESS_TOKEN = "access_token";
  public static final String FIELD_ACCESS_TOKEN_SECRET = "access_token_secret";
  public static final String FIELD_LAST_MESSAGE_ID = "last_message_id";

  private static final String[] columns = new String[] {
                                            FIELD_NETWORK_ID,
                                            FIELD_USER_ID,
                                            FIELD_ACCESS_TOKEN,
                                            FIELD_ACCESS_TOKEN_SECRET,
                                            FIELD_LAST_MESSAGE_ID,
                                          };
    

  public long networkId;
  public long userId;
  public String accessToken;
  public String accessTokenSecret;
  public long lastMessageId;

  public Network(Cursor _cur) {
    this.networkId = _cur.getLong(_cur.getColumnIndex(FIELD_NETWORK_ID));
    this.userId = _cur.getLong(_cur.getColumnIndex(FIELD_USER_ID));
    this.accessToken = _cur.getString(_cur.getColumnIndex(FIELD_ACCESS_TOKEN));
    this.accessTokenSecret = _cur.getString(_cur.getColumnIndex(FIELD_ACCESS_TOKEN_SECRET));
    this.lastMessageId = _cur.getLong(_cur.getColumnIndex(FIELD_LAST_MESSAGE_ID));
  }
  
  public Network(long _networkId, long _userId, String _token, String _secret) {
    this.networkId  = _networkId;
    this.userId = _userId;
    this.accessToken = _token;
    this.accessTokenSecret = _secret;
  }
  
  public Network save(SQLiteDatabase _db) {
    ContentValues values = toValues();

    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated Network: " + networkId);
    } else {
      _db.insertOrThrow(TABLE_NAME, null, values);                   
      if(DEBUG) Log.d(getClass().getName(), "Added Network: " + networkId);
    }

    return this;  
  }

  private String keyClause() {
    return equalClause(FIELD_NETWORK_ID, networkId);
  }
  
  private ContentValues toValues() {
    ContentValues values = new ContentValues();
    
    values.put(FIELD_NETWORK_ID, networkId);
    values.put(FIELD_USER_ID, userId);
    values.put(FIELD_ACCESS_TOKEN, accessToken);
    values.put(FIELD_ACCESS_TOKEN_SECRET, accessTokenSecret);
    values.put(FIELD_LAST_MESSAGE_ID, lastMessageId);

    return values;
  }
  
  public static Network findByNetworkId(SQLiteDatabase _db, long _networkId) {
    Cursor cur = _db.query(TABLE_NAME, columns, equalClause(FIELD_NETWORK_ID, _networkId), null, null, null, null, "1");
    try { 
      if(0 == cur.getCount()) return null;
      cur.moveToFirst();
      return new Network(cur);
    } finally {
      cur.close();
    }
  }
  
  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Network.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
  }
  
  public static void delete(SQLiteDatabase _db, long _networkId) {
    _db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + FIELD_NETWORK_ID + "=" + _networkId);
  }

  public static Network create(SQLiteDatabase _db, long _networkId, long _userId, String _token, String _secret) {
    return new Network(_networkId, _userId, _token, _secret).save(_db);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Network.class.getName(), ".onCreateDB()");
    
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_NETWORK_ID + " BIGINT NOT NULL, "
        + FIELD_USER_ID + " BIGINT UNIQUE NOT NULL, "
        + FIELD_ACCESS_TOKEN + " TEXT, "
        + FIELD_ACCESS_TOKEN_SECRET + " TEXT, "
        + FIELD_LAST_MESSAGE_ID + " BIGINT"
        + ");"
    );
  }
  
  public static void onUpgradeDB(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
    if(DEBUG) Log.d(Network.class.getName(), ".onUpgradeDB()");
    _db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreateDB(_db);
  }
}
