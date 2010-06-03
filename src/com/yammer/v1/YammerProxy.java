package com.yammer.v1;

import java.lang.IllegalArgumentException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.yammer.v1.models.Feed;
import com.yammer.v1.models.Network;
import com.yammer.v1.models.User;
import com.yammer.v1.settings.SettingsEditor;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpClient;
import net.oauth.http.HttpMessage;
import net.oauth.http.HttpResponseMessage;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class YammerProxy {

  private static final boolean DEBUG = G.DEBUG;

  public static final String DEFAULT_FEED = "My Feed";
  
  //TODO: I suspect we can get rid of these since we're using OAuth+WRAP
  private static final String PATH_CALLBACK = "/android/callback";
  private static final String PATH_REQUEST_TOKEN = "/oauth/request_token";
  private static final String PATH_AUTHORIZE = "/oauth/authorize";
  private static final String PATH_ACCESS_TOKEN = "/oauth/access_token";

  private static final String APPLICATION_NAME = "android";

  private static final String PATH_LOGIN            = "/oauth_wrap/access_token";
  private static final String PATH_CURRENT_USER     = "/api/v1/users/current.json";
  private static final String PATH_CURRENT_NETWORKS = "/api/v1/networks/current.json";
  private static final String PATH_TOKENS           = "/api/v1/oauth/tokens.json";

  private String baseURL = null;

  private String requestToken = null;
  private String tokenSecret = null;

  private OAuthAccessor accessor = null;
  private OAuthConsumer consumer = null;
  private OAuthClient client = null;
  private OAuthServiceProvider provider = null;


  @SuppressWarnings("serial")
  public static class YammerProxyException extends Exception {
    public YammerProxyException() {super();}
    public YammerProxyException(String _message, Throwable _cause) {
      super(_message);
      initCause(_cause);
    }
  }

  @SuppressWarnings("serial")
  public static class AccessDeniedException extends YammerProxyException {
    public AccessDeniedException(Throwable _cause) {
      super();
      initCause(_cause);
    }
  }

  @SuppressWarnings("serial")
  public static class ConnectionProblem extends YammerProxyException {
    public ConnectionProblem(Throwable _cause) {
      super();
      initCause(_cause);
    }
  }

  static class WRAPResponse extends OAuthMessage {
    private final HttpMessage http;

    WRAPResponse(HttpResponseMessage http) throws IOException {
        super(http.method, http.url.toExternalForm(), null);
        this.http = http;
        getHeaders().addAll(http.headers);
        for (Map.Entry<String, String> header : http.headers) {
            if ("WWW-Authenticate".equalsIgnoreCase(header.getKey())) {
                for (OAuth.Parameter parameter : decodeAuthorization(header.getValue())) {
                    if (!"realm".equalsIgnoreCase(parameter.getKey())) {
                        addParameter(parameter);
                    }
                }
            }
        }
    }


    @Override
    public InputStream getBodyAsStream() throws IOException {
      return http.getBody();
    }

    @Override
    public String getBodyEncoding() {
      return http.getContentCharset();
    }

    @Override
    protected void completeParameters() throws IOException {
      super.completeParameters();
      String body = readBodyAsString();
      if (body != null) {
        addParameters(OAuth.decodeForm(body.trim()));
      }
    }

    @Override
    protected void dump(Map<String, Object> into) throws IOException {
      super.dump(into);
      http.dump(into);
    }
  }


  private static YammerProxy proxy;
  public static YammerProxy getYammerProxy(Context _ctx) {
    String newURL = new SettingsEditor(_ctx).getUrl();
    if(null == proxy || ! newURL.equals(proxy.baseURL)) {
      proxy = new YammerProxy(newURL);
    }
    return proxy;
  }

  protected YammerProxy(String _baseURL) {
    this.baseURL = _baseURL;
    reset();
  }

  HttpClient httpClient;

  private HttpClient getHttpClient() {
    if(null == httpClient) {
      this.httpClient = new HttpClient4();
    }
    return this.httpClient;
  }

  /**
   * Reset the OAuth library
   */
  public void reset() {
    this.provider = new OAuthServiceProvider(baseURL+PATH_REQUEST_TOKEN, baseURL+PATH_AUTHORIZE, baseURL+PATH_ACCESS_TOKEN);
    this.consumer = new OAuthConsumer(baseURL+PATH_CALLBACK, OAuthCustom.KEY, OAuthCustom.SECRET, provider);
    this.accessor = new OAuthAccessor(consumer);
    this.client = new OAuthClient(getHttpClient());
    this.requestToken = null;
    this.tokenSecret = null;
  }

  /**
   * Login via OAuth+WRAP.
   *
   * @return return code
   * 200: Ok
   *  Login was successful
   * 400: Unauthorized
   *  The email or password is invalid or the network is disabled.
   * 403: Forbidden
   *  This is special and not officially part of WRAP. Some of our networks are using Single-Sign-On and have disabled password based authentication for their users. They of course want their users to be able to use our clients and they want the same or better experience as non-SSO users get.
   * 500: Internal Server Error
   *  Try again later. Chances are whatever caused the 500 will be resolved.
   */
  public int login(String _email, String _password) {

      try {
        URL url = new URL(baseURL + PATH_LOGIN +
            "?wrap_username=" + Uri.encode(_email.trim()) + 
            "&wrap_password=" + Uri.encode(_password) + 
            "&wrap_client_id=" + OAuthCustom.KEY
        );
        HttpMessage request = new HttpMessage(OAuthMessage.GET, url);
        HttpResponseMessage response = getHttpClient().execute(request);
        if(200 == response.getStatusCode()) {
          WRAPResponse wrap = new WRAPResponse(response);
          this.requestToken = wrap.getParameter("wrap_access_token");
          this.tokenSecret = wrap.getParameter("wrap_refresh_token");
        }
        return response.getStatusCode();
      } catch (MalformedURLException e1) {
        e1.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return 500;
  }

  /**
   * Request a OAuth "Request Token".
   * @throws ConnectionProblem
   */
  public void getRequestToken() throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.getRequestToken");

    if ( accessor == null ) {
      if ( DEBUG ) Log.e(getClass().getName(), "accessor not available (yet!)");
      return;
    }

    try {
      client.getRequestToken(accessor);
    } catch (java.io.IOException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    } catch (Exception e) {
      throw new ConnectionProblem(e);
    }
    // We should now have a request token and a token secret
    this.requestToken = accessor.requestToken;
    this.tokenSecret = accessor.tokenSecret;

    if (DEBUG) Log.d(getClass().getName(), "Request token: " + this.requestToken);
    if (DEBUG) Log.d(getClass().getName(), "Request token secret: " + this.tokenSecret);
  }

  /**
   * Validate the request token and the token secret.
   *
   * @return true if request token and token secret was fetched, false if a problem occured
   */
  public Boolean isRequestTokenValid() {
    if ( this.requestToken == null || this.tokenSecret == null ) {
      return false;
    }
    return true;
  }

  public void enableApplication(String callbackToken) throws AccessDeniedException {
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.enableApplication");
    Properties paramProps = new Properties();
    paramProps.setProperty("oauth_token", this.requestToken);
    try {
      OAuthMessage response = sendRequest( paramProps, this.baseURL+PATH_ACCESS_TOKEN+"?callback_token="+callbackToken, "GET");
      // Store the access token secret
      this.requestToken =  response.getParameter("oauth_token");
      this.tokenSecret =  response.getParameter("oauth_token_secret");
      if (DEBUG) Log.d(getClass().getName(), "oauth_token: " + this.requestToken);
      if (DEBUG) Log.d(getClass().getName(), "oauth_token_secret: " + this.tokenSecret);
    } catch (IOException e) {
      e.printStackTrace();
      if (DEBUG) Log.d(getClass().getName(), "IOException");
    } catch (URISyntaxException e) {
      e.printStackTrace();
      if (DEBUG) Log.d(getClass().getName(), "URISyntaxException");
    } catch (OAuthProblemException e) {
      if (DEBUG) Log.d(getClass().getName(), "HTTP status code: "+e.getHttpStatusCode());
      // Check if this is a redirect
      if (e.getHttpStatusCode() == 302) {
        if (DEBUG) Log.d( getClass().getName(), (String) e.getParameters().get(HttpResponseMessage.LOCATION) );
      }
    } catch (OAuthException e) {
      e.printStackTrace();
      if (DEBUG) Log.d(getClass().getName(), "OAuthException");
    }
  }

  /**
   * Request "Access Token".
   * @throws AccessDeniedException
   */
  public String authorizeUser() throws AccessDeniedException {
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.authorizeUser");

    assert( this.requestToken != null && this.accessor != null );

    Properties paramProps = new Properties();
    paramProps.setProperty("application_name", APPLICATION_NAME);
    paramProps.setProperty("oauth_token", requestToken);
    String responseUrl = null;
    try {
      // Submit request to obtain authorization URL
      OAuthMessage response = sendRequest(paramProps, accessor.consumer.serviceProvider.userAuthorizationURL, "GET");
      // Apparently the URL was provided without redirects
      responseUrl = response.URL;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (OAuthProblemException e) {
      if (DEBUG) Log.d(getClass().getName(), "HTTP status code: "+e.getHttpStatusCode());
      // Try to retrieve the URL that resultet in the "error"
      // We need to do this because the OAuth library creates the URL being queried
      // with signatures and all, so we let the OAuth library create the URL, try
      // to do the request and then if it fails return the URL to let us try to
      // request it with the browser.
      URL calledUrl = (URL)e.getParameters().get("URL");
      if (DEBUG) Log.d(getClass().getName(), "Called URL: "+calledUrl);
      responseUrl = calledUrl.toString();
      // Check if this is a redirect
      if (e.getHttpStatusCode() == 302) {
        if (DEBUG) Log.d(getClass().getName(), "302 Location: " + (String) e.getParameters().get(HttpResponseMessage.LOCATION));
      }
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (DEBUG) Log.d(getClass().getName(), "authorizeUserResulting URL: "+responseUrl);
    return responseUrl;
  }

  public User getCurrentUser(boolean _includeFollowed) throws YammerProxyException {
    try {
      return new User(getCurrentUserJSON(_includeFollowed));
    } catch(JSONException cause) {
      throw new YammerProxyException("Unable to get current user", cause);
    }
  }
  
  private String currentUserData;
  private JSONObject getCurrentUserJSON(boolean _includeFollowed) throws YammerProxyException {
    try {
      if(null == currentUserData) {
        String url = this.baseURL + PATH_CURRENT_USER;
        if(_includeFollowed) {
          url += "?include_followed_users=1";
        }
        currentUserData = accessResource(url);
      }
      return new JSONObject(currentUserData);
    } catch(JSONException cause) {
      throw new YammerProxyException("Unable to get current user", cause);
    }
  }
  
  /**
   * Get Networks.
   * 
   * @returns JSONMessage containing networks 
   */
  public Network[] getNetworks() throws YammerProxyException {
    try {
      
      // get base information
      JSONArray jsonArray = new JSONArray(accessResource(this.baseURL + PATH_CURRENT_NETWORKS));
      Map<Long, Network> networks = new java.util.HashMap<Long,Network>();
      for( int ii=0; ii < jsonArray.length(); ii++ ) {
        JSONObject obj = jsonArray.getJSONObject(ii);
        networks.put(obj.getLong("id"), new Network(obj));
      }
      
      // fill in tokens
      jsonArray = new JSONArray(accessResource(this.baseURL + PATH_TOKENS));
      for( int ii=0; ii < jsonArray.length(); ii++ ) {
        JSONObject obj = jsonArray.getJSONObject(ii);
        networks.get(obj.getLong("network_id")).update(obj);
      }
      
      return networks.values().toArray(new Network[networks.size()]);
    } catch (JSONException cause) {
      if (DEBUG) Log.w(getClass().getName(), cause.getMessage());
      throw new YammerProxyException("Unable to get networks", cause);
    }
  }
  
  /**
   * Set the current network.
   * 
   * All subsequent message requests will be made to this network.
   * 
   * @param _network New network
   */
  public void setCurrentNetwork(Network _network) {
    baseURL = _network.webURL;
    requestToken = _network.accessToken;
    tokenSecret = _network.accessTokenSecret;
    currentUserData = null;
  }
  
  public Feed[] getFeeds() throws YammerProxyException {
    try {
      JSONObject currentUserJSON = getCurrentUserJSON(false);
      long networkId = currentUserJSON.getLong("network_id");
      
      JSONArray jsonArray = currentUserJSON.getJSONObject("web_preferences").getJSONArray("home_tabs");
      if (DEBUG) Log.d(getClass().getName(), "Found " + jsonArray.length() + " feeds");
      Feed[] feeds = new Feed[jsonArray.length()];
      for( int ii=0; ii < jsonArray.length(); ii++ ) {
        feeds[ii] = new Feed(jsonArray.getJSONObject(ii));
        feeds[ii].networkId = networkId;
      }
      
      return feeds;
    } catch (JSONException cause) {
      throw new YammerProxyException("Unable to get feeds", cause);
    }
  }
  
  /**
   * Post a message or a reply to the current Yammer Network
   *
   * @param message - message to post
   * @param messageId - Message being replied to
   *
   * @throws YammerProxy.AccessDeniedException
   * @throws YammerProxy.ConnectionProblem
   */
  public void postMessage(final String message, final long messageId) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".postMessage");
    try {
      Properties paramProps = new Properties();
      paramProps.setProperty("oauth_token", this.requestToken);
      paramProps.setProperty("body", message);
      if( messageId != 0 ) {
        paramProps.setProperty("replied_to_id", Long.toString(messageId));
      }
      sendRequest(paramProps, this.baseURL + "/api/v1/messages/", OAuthMessage.POST);
    } catch (IllegalArgumentException ex) {
      // happens when there is a % in the body of the message
    } catch (NullPointerException e) {
      throw new ConnectionProblem(e);
    } catch (IOException e) {
      throw new ConnectionProblem(e);
    } catch (URISyntaxException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    }
  }

  /**
   * Follow user with given user ID on Yammer
   * @param userId
   *
   * @return
   *
   * @throws AccessDeniedException
   * @throws ConnectionProblem
   */
  public void followUser(long userId) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".followUser: " + userId);
    String url = this.baseURL + "/api/v1/subscriptions/";
    Properties paramProps = new Properties();
    paramProps.setProperty("target_type", "user");
    paramProps.setProperty("target_id", Long.toString(userId));
    try {
      sendRequest(paramProps, url, OAuthMessage.POST);
    } catch (IOException e) {
      throw new ConnectionProblem(e);
    } catch (URISyntaxException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    }
  }

  public void unfollowUser(long userId) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".unfollowUser: " + userId);
    String url = this.baseURL + "/api/v1/subscriptions/options";
    Properties paramProps = new Properties();
    paramProps.setProperty("target_type", "user");
    paramProps.setProperty("target_id", Long.toString(userId));
    paramProps.setProperty("oauth_token", this.requestToken);
    try {
      sendRequest(paramProps, url, "DELETE");
    } catch (IOException e) {
      throw new ConnectionProblem(e);
    } catch (URISyntaxException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    }
  }

  public String getMessagesNewerThan(String _feedURL, long _messageId) throws YammerProxyException {
    return accessResource(this.baseURL + _feedURL.substring(_feedURL.indexOf("/api/")) + ".json?newer_than=" + _messageId);
  }



  // TODO: privatize
  public String deleteResource(String url) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), ".deleteResource: " + url);
    String responseBody = null;
    Properties paramProps = new Properties();
    paramProps.setProperty("oauth_token", this.requestToken);
    try {
      OAuthMessage response = null;
      response = sendRequest(paramProps, url, "DELETE");
      responseBody = response.readBodyAsString();
    } catch (IOException e) {
      throw new ConnectionProblem(e);
    } catch (URISyntaxException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    }

    return responseBody;
  }

  // TODO: privatize
  public String accessResource(String url) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), "accessResource: " + url);
    String responseBody = null;
    try {
      Properties paramProps = new Properties();
      paramProps.setProperty("oauth_token", this.requestToken);
      responseBody = sendRequest(paramProps, url, "GET").readBodyAsString();
      if (DEBUG) Log.d(getClass().getName(), "responseBody: " + responseBody);
    } catch (NullPointerException e) {
      throw new ConnectionProblem(e);
    } catch (IOException e) {
      throw new ConnectionProblem(e);
    } catch (URISyntaxException e) {
      throw new ConnectionProblem(e);
    } catch (OAuthException e) {
      throw new AccessDeniedException(e);
    }

    return responseBody;
  }

  @SuppressWarnings("unchecked")
  private OAuthMessage sendRequest(Map map, String url, String method) throws IOException, URISyntaxException, OAuthException, AccessDeniedException {
    if (DEBUG) Log.d(getClass().getName(), ".sendRequest");

    List<Map.Entry> params = new ArrayList<Map.Entry>();
    Iterator it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry p = (Map.Entry) it.next();
      params.add(new OAuth.Parameter((String)p.getKey(), (String)p.getValue()));
    }

    try {
      accessor.tokenSecret = this.tokenSecret;

      // It seems that we need to send the auth header for Yammer at least, when submitting POST requests
      if ( method == OAuthMessage.POST ) {
        accessor.consumer.setProperty(OAuthClient.PARAMETER_STYLE, "AUTHORIZATION_HEADER");
      } else {
        accessor.consumer.setProperty(OAuthClient.PARAMETER_STYLE, "QUERY_STRING");
      }
      
      if (DEBUG) Log.d(getClass().getName(), "Invoking: " + url + " params: "+params.toString());
      return client.invoke(accessor, method, url, params);
    } catch (OAuthProblemException e) {
      int statusCode = e.getHttpStatusCode();
      if (DEBUG) Log.d(getClass().getName(), "HTTP status code: " + statusCode);
      
      // creating a message returns a status code of 201 Created
      if (201 == statusCode) {
        return null;
      }

      if (401 == statusCode) {
        throw new AccessDeniedException(e);
      }

      throw e;
    }
  }

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss ZZZZZ");

  public static long parseTime(String _date) {
    try {
      return dateFormat.parse(_date).getTime();
    } catch (ParseException e) {
      if(DEBUG) Log.e(YammerProxy.class.getName(), "Could not parse date", e);
    }
    return System.currentTimeMillis();
  }

}
