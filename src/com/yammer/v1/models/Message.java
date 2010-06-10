package com.yammer.v1.models;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.yammer.v1.G;
import com.yammer.v1.YammerProxy;

public class Message extends Base {

  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "messages";
  
  public static final String FIELD_USER_ID = User.FIELD_USER_ID;
  public static final String FIELD_MESSAGE_ID = "message_id";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_TIMESTAMP = "timestamp";
  public static final String FIELD_SENDER_TYPE = "sender_type";
  public static final String FIELD_THREAD_ID = "thread_URL_id";
  public static final String FIELD_CLIENT_TYPE = "client_type";
  public static final String FIELD_REPLIED_TO_ID = "replied_to_id";
  public static final String FIELD_CREATED_AT = "created_at";
  public static final String FIELD_NETWORK_ID = Network.FIELD_NETWORK_ID;
  public static final String FIELD_DELETED = "deleted";

  private static final String[] columns = new String[] {
    FIELD_USER_ID,
    FIELD_MESSAGE_ID,
    FIELD_MESSAGE,
    FIELD_TIMESTAMP,
    FIELD_SENDER_TYPE,
    FIELD_THREAD_ID,
    FIELD_CLIENT_TYPE,
    FIELD_REPLIED_TO_ID,
    FIELD_CREATED_AT,
    FIELD_NETWORK_ID,
    FIELD_DELETED,
  };

  public long userId;
  public long messageId;
  public String message;
  public Date timestamp;
  public String senderType;
  public long threadId;
  public String clientType;
  public Long repliedToId;
  public String createdAt;
  public long networkId;
  public boolean deleted;
  
  public URL[] urls;

  public Message(JSONObject _obj, long _networkId) throws JSONException {
    JSONObject body = _obj.getJSONObject("body");
    this.message = body.getString("plain");
    this.messageId = _obj.getLong("id");
    this.networkId = _networkId;
    this.userId = _obj.getLong("sender_id");
    this.senderType = _obj.getString("sender_type");
    this.threadId = _obj.getLong("thread_id");
    this.clientType = _obj.getString("client_type");
    if(!_obj.isNull("replied_to_id")) {
      this.repliedToId = _obj.getLong("replied_to_id");
    }
    this.createdAt = _obj.getString("created_at");
    this.timestamp = new Date(YammerProxy.parseTime(this.createdAt));

    if(body.has("urls")) {
      JSONArray jsonArray = body.getJSONArray("urls");
      if (DEBUG) Log.d(getClass().getName(), "jsonArray.length(): " + jsonArray.length());
      this.urls = new URL[jsonArray.length()];
      for( int ii=0; ii < jsonArray.length(); ii++ ) {
        this.urls[ii] = new URL(this.messageId, this.networkId, jsonArray.getString(ii));
        if (DEBUG) Log.d(getClass().getName(), "Extracted URL: " + this.urls[ii]);
      }
    }
  }

  public Message(Cursor _cur) {
    userId = _cur.getLong(_cur.getColumnIndex(FIELD_USER_ID));
    messageId = _cur.getLong(_cur.getColumnIndex(FIELD_MESSAGE_ID));
    message = _cur.getString(_cur.getColumnIndex(FIELD_MESSAGE));
    timestamp = new Date(_cur.getLong(_cur.getColumnIndex(FIELD_TIMESTAMP)));
    senderType = _cur.getString(_cur.getColumnIndex(FIELD_SENDER_TYPE));
    threadId = _cur.getLong(_cur.getColumnIndex(FIELD_THREAD_ID));
    clientType = _cur.getString(_cur.getColumnIndex(FIELD_CLIENT_TYPE));
    repliedToId = _cur.getLong(_cur.getColumnIndex(FIELD_REPLIED_TO_ID));
    createdAt = _cur.getString(_cur.getColumnIndex(FIELD_CREATED_AT));
    networkId = _cur.getLong(_cur.getColumnIndex(FIELD_NETWORK_ID));
    deleted = (1 == _cur.getInt(_cur.getColumnIndex(FIELD_DELETED)));
  }

  private ContentValues toValues() {
    ContentValues values = new ContentValues();
    
    values.put(FIELD_MESSAGE, message);
    values.put(FIELD_MESSAGE_ID, messageId);
    values.put(FIELD_USER_ID, userId);
    values.put(FIELD_SENDER_TYPE, senderType);
    values.put(FIELD_THREAD_ID, threadId);
    values.put(FIELD_CLIENT_TYPE, clientType);
    if(null == repliedToId) {
      values.put(FIELD_REPLIED_TO_ID, "null");
    } else {
      values.put(FIELD_REPLIED_TO_ID, repliedToId);
    }
    values.put(FIELD_NETWORK_ID, networkId);
    values.put(FIELD_CREATED_AT, createdAt);
    values.put(FIELD_TIMESTAMP, timestamp.getTime());
    values.put(FIELD_DELETED, deleted ? 1 : 0);

    return values;
  }

  public Message save(SQLiteDatabase _db) {

    ContentValues values = toValues();
      
    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated message: " + messageId);
    } else {
      _db.insert(TABLE_NAME, null, values);
      if(DEBUG) Log.d(getClass().getName(), "Inserted message: " + messageId);
    }
    
    if(null != this.urls) {
      for(int ii=0; ii < this.urls.length ;ii++) {
        this.urls[ii].save(_db);
      }
    }
    
    return this;
  }

  private String keyClause() {
    return equalClause(FIELD_MESSAGE_ID, messageId);
  }

  public static long getFirstMessageId(SQLiteDatabase _db, long _networkId) {
    Cursor c = null;
    try {
      c = _db.query(
            TABLE_NAME, 
            new String[] {"MIN(" + FIELD_MESSAGE_ID + ")"}, 
            FIELD_NETWORK_ID + "=" + _networkId, 
            null, null, null, null
          );
      c.moveToFirst();
      return c.getLong(0);
    } finally {
      if(null != c) {
        c.close();
      }
    }
  } 

  public static long getLastMessageId(SQLiteDatabase _db, long _networkId) {
    Cursor c = null;
    try {
      c = _db.query(
            TABLE_NAME, 
            new String[] {"MAX(" + FIELD_MESSAGE_ID + ")"}, 
            FIELD_NETWORK_ID + "=" + _networkId, 
            null, null, null, null
          );
      c.moveToFirst();
      return c.getLong(0);
    } finally {
      if(null != c) {
        c.close();
      }
    }
  } 

  public static Message findById(SQLiteDatabase _db, long _id) {
    Cursor c = null;
    try {
      c = _db.query(TABLE_NAME, columns, _ID + "=" + _id, null, null, null, null);
      c.moveToFirst();
      if(0 == c.getCount()) {
        return null;
      } else {
        return new Message(c);
      }
    } finally {
      if(null != c) {
        c.close();
      }
    }
  }

  public static Message create(SQLiteDatabase _db, JSONObject _obj, long _netoworkId) throws JSONException {
    return new Message(_obj, _netoworkId).save(_db);
  }

  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Message.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
    URL.deleteAll(_db);
  }

  public static void deleteByMessageId(SQLiteDatabase _db, long _messageId) {
    delete(_db, equalClause(FIELD_MESSAGE_ID, _messageId));
  }

  public static void deleteByNetworkId(SQLiteDatabase _db, long _networkId) {
    delete(_db, equalClause(FIELD_NETWORK_ID, _networkId));
  }
  
  public static void delete(SQLiteDatabase _db, String _clauseWithoutWhere) {
    _db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + _clauseWithoutWhere);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(Message.class.getName(), ".onCreateDB()");
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" ("
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " 
        + FIELD_TIMESTAMP + " INTEGER, " 
        + FIELD_MESSAGE + " TEXT NOT NULL, "
        + FIELD_USER_ID + " BIGINT, "
        + FIELD_MESSAGE_ID + " BIGINT UNIQUE NOT NULL, "
        + FIELD_SENDER_TYPE + " TEXT, "
        + FIELD_THREAD_ID + " BIGINT, "
        + FIELD_CLIENT_TYPE + " TEXT, "
        + FIELD_REPLIED_TO_ID + " BIGINT, "
        + FIELD_CREATED_AT + " TEXT, "
        + FIELD_NETWORK_ID + " BIGINT, "
        + FIELD_DELETED + " BOOLEAN DEFAULT 0"
        + ");"
    );
    
  }
  
  public static void onUpgradeDB(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
    if(DEBUG) Log.d(Message.class.getName(), ".onUpgradeDB()");
    _db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreateDB(_db);
  }

}
