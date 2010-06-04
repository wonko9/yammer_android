package com.yammer.v1.models;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.yammer.v1.G;

public class URL extends Base {

  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "urls";
  
  public static final String FIELD_MESSAGE_ID = "message_id";
  public static final String FIELD_NETWORK_ID = "network_id";
  public static final String FIELD_URL = "url";       
  public static final String FIELD_TITLE = "title"; 
  public static final String FIELD_FAVICON_ID = "favicon_id";       
  
  public long messageId;
  public long networkId;
  public String url;
  public String title;
  public String faviconId;
  
  public URL(long _messageId, long _networkId, String _url) {
    this.messageId = _messageId;
    this.networkId = _networkId;
    this.url = _url;
  }
  
  public URL save(SQLiteDatabase _db) {
    
    ContentValues values = toValues();

    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated URL: " + url);
    } else {
      _db.insert(TABLE_NAME, null, values);                   
      if(DEBUG) Log.d(getClass().getName(), "Added URL: " + url);
    }

    return this;
  }
  
  private String keyClause() {
    return equalClause(FIELD_MESSAGE_ID, messageId) + " AND " + equalClause(FIELD_NETWORK_ID, networkId);
  }
  
  private ContentValues toValues() {
    ContentValues values = new ContentValues();
    
    values.put(FIELD_MESSAGE_ID, messageId);
    values.put(FIELD_NETWORK_ID, networkId);
    values.put(FIELD_URL, url);
    values.put(FIELD_TITLE, title);
    values.put(FIELD_FAVICON_ID, faviconId);

    return values;
  }
  
  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(URL.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(URL.class.getName(), ".onCreateDB()");
    
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_MESSAGE_ID + " BIGINT NOT NULL, "
        + FIELD_NETWORK_ID + " BIGINT NOT NULL, "
        + FIELD_URL + " TEXT, "
        + FIELD_TITLE + " TEXT, "
        + FIELD_FAVICON_ID + " TEXT"
        + ");"
    );
  }
  
  public static void onUpgradeDB(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
    if(DEBUG) Log.d(URL.class.getName(), ".onUpgradeDB()");
    _db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreateDB(_db);
  }

}
