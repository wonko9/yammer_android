package com.yammer.v1.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.yammer.v1.G;
import com.yammer.v1.Utils;
import com.yammer.v1.Block;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * ActiveModel User class.
 * 
 * @author ss
 */
public class User extends Base {
  
  private static final boolean DEBUG = G.DEBUG;

  public static final String TABLE_NAME = "users";
  
  public static final String FIELD_USER_ID = "user_id";
  public static final String FIELD_MUGSHOT_URL = "mugshot_url"; 
  public static final String FIELD_MUGSHOT_MD5 = "mugshot_md5"; 
  public static final String FIELD_FULL_NAME = "full_name";
  public static final String FIELD_REPLYEE_FULL_NAME = "replyee_full_name";
  public static final String FIELD_NAME = "name";
  public static final String FIELD_TITLE = "title";
  public static final String FIELD_EMAIL = "email";
  public static final String FIELD_REPLYEE_EMAIL = "replyee_email";
  public static final String FIELD_URL = "url";
  public static final String FIELD_WEB_URL = "web_url";
  public static final String FIELD_IS_FOLLOWING = "is_following";
  
  public long userId;
  public String mugshotURL;
  public String mugshotMD5;
  public String fullName;
  public String name;
  public String email;
  public String title;
  public String url;
  public boolean following;
  public String webURL;

  User(Cursor _cur) {
    this.userId = _cur.getLong(_cur.getColumnIndex(FIELD_USER_ID));
    this.mugshotURL = _cur.getString(_cur.getColumnIndex(FIELD_MUGSHOT_URL));
    this.mugshotMD5 = _cur.getString(_cur.getColumnIndex(FIELD_MUGSHOT_MD5));
    this.fullName = _cur.getString(_cur.getColumnIndex(FIELD_FULL_NAME));
    this.name = _cur.getString(_cur.getColumnIndex(FIELD_NAME));
    this.title = _cur.getString(_cur.getColumnIndex(FIELD_TITLE));
    this.email = _cur.getString(_cur.getColumnIndex(FIELD_EMAIL));
    this.url = _cur.getString(_cur.getColumnIndex(FIELD_URL));
    this.following = (1 == _cur.getInt(_cur.getColumnIndex(FIELD_IS_FOLLOWING)));
    this.webURL = _cur.getString(_cur.getColumnIndex(FIELD_WEB_URL));
  }
  
  User(Cursor _cur, Block<User, Void> _block) {
    this(_cur);
    _block.call(this);
  }
  
  User(JSONObject _user, boolean _following) throws JSONException {
    this.userId = _user.getLong("id");
    this.name = _user.getString("name");
    this.fullName = _user.getString("full_name");
    this.title = _user.getString("job_title");
    this.mugshotURL = _user.getString("mugshot_url");
    this.mugshotMD5 = Utils.md5(_user.getString("mugshot_url"));
    this.webURL = _user.getString("web_url");
    this.url = _user.getString("url");
    this.email = getEmailAddress(_user, "primary");
    this.following = _following;
  }

  User(JSONObject _obj, boolean _following, Block<User, Void> _block) throws JSONException {
    this(_obj, _following);
    _block.call(this);
  }

  private String getEmailAddress(JSONObject _user, String _type) {
    try {
      JSONArray addresses = _user.getJSONObject("contact").getJSONArray("email_addresses");
      for(int ii=0; ii < addresses.length() ;ii++) {
        JSONObject entry = addresses.getJSONObject(ii);
        if(_type.equals(entry.getString("type"))) return entry.getString("address");
      }
    } catch(JSONException ex) {
      // swallow
    }
    return null;
  }
  
  private ContentValues toValues() {
    ContentValues values = new ContentValues();
    
    values.put(FIELD_USER_ID, this.userId);
    values.put(FIELD_NAME, this.name);
    values.put(FIELD_FULL_NAME, this.fullName);
    values.put(FIELD_TITLE, this.title);
    values.put(FIELD_MUGSHOT_URL, this.mugshotURL);
    values.put(FIELD_MUGSHOT_MD5, this.mugshotMD5);
    values.put(FIELD_WEB_URL, this.webURL);
    values.put(FIELD_URL, this.url);
    values.put(FIELD_EMAIL, this.email);
    values.put(FIELD_IS_FOLLOWING, this.following ? 1 : 0);

    return values;
  }

  public User save(SQLiteDatabase _db) {
    ContentValues values = toValues();

    if(0 != _db.update(TABLE_NAME, values, keyClause(), null)) {
      if(DEBUG) Log.d(getClass().getName(), "Updated URL: " + url);
    } else {
      _db.insertOrThrow(TABLE_NAME, null, values);                   
      if(DEBUG) Log.d(getClass().getName(), "Added URL: " + url);
    }

    return this;
  }
  
  private String keyClause() {
    return equalClause(FIELD_USER_ID, userId);
  }
  
  public static User create(SQLiteDatabase _db, JSONObject _obj, boolean _following) throws JSONException, SQLiteConstraintException {
    return new User(_obj, _following).save(_db);
  }
  
  public static void each(SQLiteDatabase _db, String[] _cols, boolean _following, Block<User, Void> _block) {
    if(DEBUG) Log.d(User.class.getName(), ".each()");
    
    String where = null;
    if(_following) {
      where = equalClause(FIELD_IS_FOLLOWING, "1");
    }
    
    Cursor c = _db.query(TABLE_NAME, _cols, where, null, null, null, FIELD_FULL_NAME);
    c.moveToFirst();
    while(!c.isAfterLast()) {
      _block.call(new User(c));
    }
    c.close();
  }
  
  public static void deleteAll(SQLiteDatabase _db) {
    if(DEBUG) Log.d(User.class.getName(), ".deleteAll()");
    _db.execSQL("DELETE FROM " + TABLE_NAME);
  }

  public static void onCreateDB(SQLiteDatabase _db) {
    if(DEBUG) Log.d(User.class.getName(), ".onCreateDB()");
    
    _db.execSQL( "CREATE TABLE " + TABLE_NAME +" (" 
        + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + FIELD_USER_ID + " BIGINT UNIQUE NOT NULL, "
        + FIELD_MUGSHOT_URL + " TEXT, "
        + FIELD_MUGSHOT_MD5 + " TEXT, "
        + FIELD_FULL_NAME + " TEXT, "
        + FIELD_NAME + " TEXT, "
        + FIELD_TITLE + " TEXT, "
        + FIELD_EMAIL + " TEXT, "
        + FIELD_URL + " TEXT, "
        + FIELD_IS_FOLLOWING + " BOOLEAN DEFAULT 0, "
        + FIELD_WEB_URL + " TEXT"
        + ");"
    );
  }
  
  public static void onUpgradeDB(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
    if(DEBUG) Log.d(User.class.getName(), ".onUpgradeDB()");
    _db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreateDB(_db);
  }
}