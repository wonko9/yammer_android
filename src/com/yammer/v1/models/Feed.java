package com.yammer.v1.models;

import org.json.JSONException;
import org.json.JSONObject;

import com.yammer.v1.G;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class Feed extends Base {

  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "feeds";
  
  public static final String FIELD_NAME         = "name";
  public static final String FIELD_NETWORK_ID   = "network_id";
  public static final String FIELD_URL          = "url";
  public static final String FIELD_DESCRIPTION  = "description";
  public static final String FIELD_ORDER        = "order_index";
  
  public String name;
  public long networkId;
  public String url;
  public String description;
  public int orderIndex;

  public Feed(Cursor _cur) {
    this.name = _cur.getString(_cur.getColumnIndex(FIELD_NAME));
    this.networkId = _cur.getLong(_cur.getColumnIndex(FIELD_NETWORK_ID));
    this.url = _cur.getString(_cur.getColumnIndex(FIELD_URL));
    this.description = _cur.getString(_cur.getColumnIndex(FIELD_DESCRIPTION));
    this.orderIndex = _cur.getInt(_cur.getColumnIndex(FIELD_ORDER));
  }
  
  public Feed(JSONObject _json) throws JSONException {
    this.name = _json.getString("name");
    this.url = _json.getString("url");
    this.description = _json.getString("feed_description");
    this.orderIndex = _json.getInt("ordering_index");
  }
  
  private ContentValues toValues() {
    ContentValues values = new ContentValues();
    
    values.put(FIELD_NAME, this.name);
    values.put(FIELD_NETWORK_ID, this.networkId);
    values.put(FIELD_URL, this.url);
    values.put(FIELD_DESCRIPTION, this.description);
    values.put(FIELD_ORDER, this.orderIndex);
    
    return values;
  }

  public Feed save(SQLiteDatabase _db) {
    ContentValues values = toValues();

    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated Feed: " + name);
    } else {
      _db.insert(TABLE_NAME, null, values);                   
      if(DEBUG) Log.d(getClass().getName(), "Added Feed: " + name);
    }

    return this;
  }
  
  private String keyClause() {
    return equalClause(FIELD_NAME, name);
  }
  
  public static Feed create(SQLiteDatabase _db, JSONObject _obj) throws JSONException, SQLiteConstraintException {
    return new Feed(_obj).save(_db);
  }
  
  public static String[] getFeedNames(SQLiteDatabase _db, long _networkId) {
    Cursor cur = null;
    try {
      cur = _db.query(
                      TABLE_NAME, 
                      new String[] {FIELD_NAME}, 
                      equalClause(FIELD_NETWORK_ID, _networkId),
                      null, null, null, 
                      FIELD_ORDER
                   );
      String[] names = new String[cur.getCount()]; 
      cur.moveToFirst();
      for (int ii=0 ; ii < names.length ; ii++) {
        names[ii] = cur.getString(0);
        cur.moveToNext();
      }
      
      return names;
    } finally {
      if(null != cur) {
        cur.close();
      }
    }
    
  }
  
  public static String getURLForFeed(SQLiteDatabase _db, long _networkId, String _name) {
    Cursor cur = null;
    try {
      cur = _db.query(
                      TABLE_NAME, 
                      new String[] {FIELD_URL},
                      equalClause(FIELD_NETWORK_ID, _networkId) + " AND " + equalClause(FIELD_NAME, _name),
                      null, null, null, null
                    );
      cur.moveToFirst();
      return cur.getString(cur.getColumnIndex(FIELD_URL));
    } finally {
      if(null != cur) {
        cur.close();
      }
    }
  }

  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Feed.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
  }

  public static void deleteByNetworkId(SQLiteDatabase _db, long _networkId) {
    delete(_db, equalClause(FIELD_NETWORK_ID, _networkId));
  }
  
  public static void delete(SQLiteDatabase _db, String _clauseWithoutWhere) {
    _db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + _clauseWithoutWhere);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Feed.class.getName(), ".onCreateDB()");
    
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_NAME + " TEXT, "
        + FIELD_NETWORK_ID + " BIGINT, "
        + FIELD_URL + " TEXT, "
        + FIELD_DESCRIPTION + " TEXT, "
        + FIELD_ORDER + " INTEGER "
        + ");"
    );
  }
  
  public static void onUpgradeDB(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
    if(DEBUG) Log.d(Feed.class.getName(), ".onUpgradeDB()");
    _db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreateDB(_db);
  }

}
