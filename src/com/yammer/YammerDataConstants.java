package com.yammer;

import android.provider.BaseColumns;

public interface YammerDataConstants extends BaseColumns {
	public static final String TABLE_MESSAGES = "messages";
	public static final String TABLE_USERS = "users";
	public static final String TABLE_NETWORKS = "networks";
	public static final String TABLE_URLS = "urls";

	// Columns in "messages" database
	public static final String SENDER_ID = "user_id";
	public static final String MESSAGE_ID = "message_id";
	public static final String MESSAGE = "message";
	public static final String TIMESTAMP = "timestamp";
	public static final String SENDER_TYPE = "sender_type";
	public static final String THREAD_ID = "thread_id";
	public static final String CLIENT_TYPE = "client_type";
	public static final String REPLIED_TO_ID = "replied_to_id";
	public static final String CREATED_AT = "created_at";
	public static final String DELETED = "deleted";
	
	// Columns in "users" database
	public static final String USER_ID = "user_id";
	public static final String MUGSHOT_URL = "mugshot_url";	
	public static final String MUGSHOT_MD5 = "mugshot_md5";	
	public static final String FULL_NAME = "full_name";
	public static final String REPLYEE_FULL_NAME = "replyee_full_name";
	public static final String NAME = "name";
	public static final String TITLE = "title";
	public static final String EMAIL = "email";
	public static final String REPLYEE_EMAIL = "replyee_email";
	public static final String URL = "url";
	public static final String WEB_URL = "web_url";
	public static final String IS_FOLLOWING = "is_following";
	
	// Columns in "networks" database
	public static final String NETWORK_ID = "network_id";
	public static final String ACCESS_TOKEN = "access_token";
	public static final String ACCESS_TOKEN_SECRET = "access_token_secret";
	public static final String LAST_MESSAGE_ID = "last_message_id";

	// Columns in "urls" table
	//public static final String MESSAGE_ID = "message_id";
	//public static final String URL = "url";	
	public static final String URL_TITLE = "title";	
	public static final String URL_FAVICON_ID = "favicon_id";	
	
	// TODO: Threads table
}
