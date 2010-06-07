package com.yammer.v1.models;

import org.json.JSONException;
import org.json.JSONObject;

import com.yammer.v1.G;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class Network extends Base {

  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "networks";
  
  public static final String FIELD_NETWORK_ID           = "network_id";
  public static final String FIELD_USER_ID              = User.FIELD_USER_ID;
  public static final String FIELD_NAME                 = "name";
  public static final String FIELD_WEB_URL              = "web_url";
  public static final String FIELD_ACCESS_TOKEN         = "access_token";
  public static final String FIELD_ACCESS_TOKEN_SECRET  = "access_token_secret";
  public static final String FIELD_LAST_MESSAGE_ID      = "last_message_id";

  private static final String[] columns = new String[] {
                                            FIELD_NETWORK_ID,
                                            FIELD_USER_ID,
                                            FIELD_NAME,
                                            FIELD_WEB_URL,
                                            FIELD_ACCESS_TOKEN,
                                            FIELD_ACCESS_TOKEN_SECRET,
                                            FIELD_LAST_MESSAGE_ID,
                                          };
    

  public long networkId;
  public long userId;
  public String name;
  public String webURL;
  public String accessToken;
  public String accessTokenSecret;
  public long lastMessageId = 0L;

  public Network(Cursor _cur) {
    this.networkId = _cur.getLong(_cur.getColumnIndex(FIELD_NETWORK_ID));
    this.userId = _cur.getLong(_cur.getColumnIndex(FIELD_USER_ID));
    this.name = _cur.getString(_cur.getColumnIndex(FIELD_NAME));
    this.webURL = _cur.getString(_cur.getColumnIndex(FIELD_WEB_URL));
    this.accessToken = _cur.getString(_cur.getColumnIndex(FIELD_ACCESS_TOKEN));
    this.accessTokenSecret = _cur.getString(_cur.getColumnIndex(FIELD_ACCESS_TOKEN_SECRET));
    this.lastMessageId = _cur.getLong(_cur.getColumnIndex(FIELD_LAST_MESSAGE_ID));
  }

  public Network(JSONObject _json) throws JSONException {
    update(_json);
  }
  
  public void update(JSONObject _json) throws JSONException {
    if(_json.has("id")) this.networkId = _json.getLong("id");
    if(_json.has("user_id")) this.userId = _json.getLong("user_id");
    if(_json.has("name")) this.name = _json.getString("name");
    if(_json.has("web_url")) this.webURL = _json.getString("web_url");
    if(_json.has("token")) this.accessToken = _json.getString("token");
    if(_json.has("secret")) this.accessTokenSecret = _json.getString("secret");    
  }
  
  public Network save(SQLiteDatabase _db) {
    ContentValues values = toValues();

    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated Network: " + name);
    } else {
      _db.insert(TABLE_NAME, null, values);                   
      if(DEBUG) Log.d(getClass().getName(), "Added Network: " + name);
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
    values.put(FIELD_NAME, name);
    values.put(FIELD_WEB_URL, webURL);
    values.put(FIELD_ACCESS_TOKEN, accessToken);
    values.put(FIELD_ACCESS_TOKEN_SECRET, accessTokenSecret);
    values.put(FIELD_LAST_MESSAGE_ID, lastMessageId);

    return values;
  }
  
  public static Network findByNetworkId(SQLiteDatabase _db, long _id) {
    Cursor cur = null;
    try { 
      cur = _db.query(TABLE_NAME, columns, equalClause(FIELD_NETWORK_ID, _id), null, null, null, null, "1");
      if(0 == cur.getCount()) return null;
      cur.moveToFirst();
      
      return new Network(cur);
    } finally {
      if(null != cur) {
        cur.close();
      }
    }
  }

  public static Network[] getAll(SQLiteDatabase _db) {
    Cursor cur = null;
    try {
      cur = _db.query(TABLE_NAME, columns, null, null, null, null, FIELD_NAME);
      Network[] networks = new Network[cur.getCount()]; 
      cur.moveToFirst();
      for (int ii=0 ; ii < networks.length ; ii++) {
        networks[ii] = new Network(cur);
        cur.moveToNext();
      }
      
      return networks;
    } finally {
      if(null != cur) {
        cur.close();
      }
    }
  }
  

  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Network.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
  }
  
  public static void delete(SQLiteDatabase _db, long _networkId) {
    _db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + FIELD_NETWORK_ID + "=" + _networkId);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Network.class.getName(), ".onCreateDB()");
    
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_NETWORK_ID + " BIGINT NOT NULL, "
        + FIELD_USER_ID + " BIGINT UNIQUE NOT NULL, "
        + FIELD_NAME + " TEXT, "
        + FIELD_WEB_URL + " TEXT, "
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
