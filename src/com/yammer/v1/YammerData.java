package com.yammer.v1;

import com.yammer.v1.models.Feed;
import com.yammer.v1.models.Network;
import com.yammer.v1.models.URL;
import com.yammer.v1.models.User;
import com.yammer.v1.models.Message;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

// TODO: Dry up getWritableDatabase() calls

public class YammerData extends SQLiteOpenHelper {

  private static final boolean DEBUG = G.DEBUG;

  @SuppressWarnings("serial")
  public static class YammerDataException extends Exception {
    public YammerDataException(Exception _cause) {
      super();
      initCause(_cause);
    }
  }

  private static final String DATABASE_NAME = "yammer.db";
  private static final int DATABASE_VERSION = 30;

  public YammerData(Context ctx) {
    super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public void onCreate(SQLiteDatabase db) {
    if (DEBUG) Log.d(getClass().getName(), ".onCreate");
    Message.onCreateDB(db);
    User.onCreateDB(db);
    Network.onCreateDB(db);
    URL.onCreateDB(db);
    Feed.onCreateDB(db);
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (DEBUG) Log.i(getClass().getName(), ".onUpgrade");
    Message.onUpgradeDB(db, oldVersion, newVersion);
    User.onUpgradeDB(db, oldVersion, newVersion);
    Network.onUpgradeDB(db, oldVersion, newVersion);
    URL.onUpgradeDB(db, oldVersion, newVersion);
    Feed.onUpgradeDB(db, oldVersion, newVersion);
  }

  public void resetData(long networkId) {
    if (DEBUG) Log.d(getClass().getName(), ".resetData");
    SQLiteDatabase db = this.getWritableDatabase();
    Message.deleteByNetworkId(db, networkId);
    Feed.deleteByNetworkId(db, networkId);
    Network.delete(db, networkId);
    clearFeeds();
  }

  public static int ID_TARGET_USER = 0;
  public static int ID_TARGET_TAG = 1;

  public void addNetworks(Network[] _networks) {
    for(int ii=0; ii < _networks.length ;ii++) {
      _networks[ii].save(getWritableDatabase());
    }
  }

  public Network getNetwork(long networkId) {
    return Network.findByNetworkId(getReadableDatabase(), networkId);
  }
  
  public Network[] getNetworks() {
    return Network.getAll(getReadableDatabase());
  }

  public void clearNetworks() {
    if (DEBUG) Log.d(getClass().getName(), ".clearNetworks");
    Network.deleteAll(getWritableDatabase());
  }


  public void save(Network _value) {
    _value.save(getWritableDatabase());
  }

  public Message getMessage(long _id) {
    return Message.findById(getReadableDatabase(), _id);
  }
  /**
   * Get the highest message ID in the messages table
   * @param networkId
   * @returns the highest message ID in the messages table
   */
  public long getLastMessageId(long networkId) {
    return Message.getLastMessageId(getReadableDatabase(), networkId);
  }

  /**
   * Get the lowest message ID in the messages table
   * @param networkId
   * @returns the lowest message ID in the messages table
   */
  public long getFirstMessageId(long networkId) {
    return Message.getFirstMessageId(getReadableDatabase(), networkId);
  }

  public Message addMessage(JSONObject _json, long _networkId) throws YammerDataException {
    try {
      return Message.create(getWritableDatabase(), _json, _networkId);
    } catch(JSONException e) {
      throw new YammerDataException(e);
    }
  }
  public void deleteMessage(long messageId) {
    Message.deleteByMessageId(getWritableDatabase(), messageId);
  }
  
  public void clearMessages() {
    if (DEBUG) Log.d(getClass().getName(), ".resetData");
    Message.deleteAll(getWritableDatabase());
  }


  public void clearUsers() {
    User.deleteAll(getWritableDatabase());
  }
  
  public User addUser(JSONObject _json) throws YammerDataException {
    try {
      return User.create(getWritableDatabase(), _json, false);
    } catch(JSONException e) {
      throw new YammerDataException(e);
    }
  }
  
  public void saveUsers(User[] _users) {
    for(int ii=0; ii < _users.length ;ii++) {
      _users[ii].save(getWritableDatabase());
    }
  }

  public void clearFeeds() {
    if (DEBUG) Log.d(getClass().getName(), ".clearFeeds");
    Feed.deleteAll(getWritableDatabase());
  }
  
  public void deleteFeedsFor(long _networkId) {
    if (DEBUG) Log.d(getClass().getName(), ".deleteFeedsFor: " + _networkId);
    Feed.deleteByNetworkId(getWritableDatabase(), _networkId);
  }

  public void addFeeds(Feed[] _feeds) {
    for(int ii=0; ii < _feeds.length ;ii++) {
      _feeds[ii].save(getWritableDatabase());
    }
  }

  public String[] getFeedNames(long _networkId) {
    return Feed.getFeedNames(getReadableDatabase(), _networkId);
  }
  
  public String getURLForFeed(long _networkId, String _name) throws YammerDataException {
    try {
      return Feed.getURLForFeed(getReadableDatabase(), _networkId, _name);
    } catch(CursorIndexOutOfBoundsException ex) {
      throw new YammerDataException(ex);
    }
  }
  
}
