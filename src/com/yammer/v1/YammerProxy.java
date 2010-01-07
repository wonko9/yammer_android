package com.yammer.v1;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpResponseMessage;
import android.util.Log;

public class YammerProxy {
  
  private static final boolean DEBUG = G.DEBUG;
  
  public static final String DEFAULT_FEED = "My Feed";
  
  @SuppressWarnings("serial")
  public static class YammerProxyException extends Exception {
  }

  @SuppressWarnings("serial")
  public static class AccessDeniedException extends YammerProxyException {
  }

  @SuppressWarnings("serial")
  public static class ConnectionProblem extends YammerProxyException {
  }


  /**
   * Properties for service we are trying to authenticate against.
   * 
   * Yammer properties:
   */
  String baseURL = null;
  String applicationName = "android";
  String callbackPath = "/android/callback";
  String reqPath = "/oauth/request_token";
  String authzPath = "/oauth/authorize";
  String accessPath = "/oauth/access_token";

  String requestToken = null;
  String tokenSecret = null;

  /**
   * These are the properties that should be set by the values 
   * returned by the server.	 
   */
  private OAuthAccessor accessor = null;
  private OAuthConsumer consumer = null; 
  private OAuthClient client = null;
  private OAuthServiceProvider provider = null;

  public YammerProxy(String _baseURL) {
    this.baseURL = _baseURL;
    reset();
  }

  /**
   * Reset the OAuth library
   */
  public void reset() {
    this.provider = new OAuthServiceProvider(baseURL+reqPath, baseURL+authzPath, baseURL+accessPath);
    this.consumer = new OAuthConsumer(baseURL+callbackPath, OAuthCustom.KEY, OAuthCustom.SECRET, provider);        
    this.accessor = new OAuthAccessor(consumer);
    this.client = new OAuthClient(new HttpClient4());
    this.requestToken = null;
    this.tokenSecret = null;
  }

  /**
   * Request a OAuth "Request Token".
   * @throws ConnectionProblem 
   */
  public void getRequestToken() throws ConnectionProblem {    	
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.getRequestToken");

    if ( accessor == null ) {
      if ( DEBUG ) Log.e(getClass().getName(), "accessor not available (yet!)");
      return;
    }   

    try {
      client.getRequestToken(accessor);
    } catch (java.io.IOException e) {
      if (DEBUG) Log.e("yammerApp", "IOException: " + e.toString());    	
      throw new ConnectionProblem();
    } catch (OAuthException e) {
      if (DEBUG) Log.e("yammerApp", "OAuthException: " + e.toString());
      throw new ConnectionProblem();
    } catch (Exception e) {
      // Do something
      if (DEBUG) Log.e("yammerApp", "An unknown error occured: " + e.toString());
      throw new ConnectionProblem();
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
      // Something's wrong
      return false;
    }
    // A OK
    return true;
  }

  public void enableApplication(String callbackToken) throws AccessDeniedException {
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.enableApplication");
    Properties paramProps = new Properties();
    paramProps.setProperty("oauth_token", this.requestToken);
    try {
      OAuthMessage response = sendRequest( paramProps, this.baseURL+this.accessPath+"?callback_token="+callbackToken, "GET");
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
    paramProps.setProperty("application_name", applicationName);
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
    Properties paramProps = new Properties();
    paramProps.setProperty("oauth_token", this.requestToken);
    paramProps.setProperty("body", message);
    if( messageId != 0 ) {
      paramProps.setProperty("replied_to_id", Long.toString(messageId));
    }
    try {
      sendRequest(paramProps, this.baseURL + "/api/v1/messages/", "POST");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new ConnectionProblem();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
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
      sendRequest(paramProps, url, "POST");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new ConnectionProblem();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
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
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new ConnectionProblem();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
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
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new ConnectionProblem();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }    	

    return responseBody;
  }

  // TODO: privatize
  public String accessResource(String url) throws YammerProxyException {
    if (DEBUG) Log.d(getClass().getName(), "Accessing resource: " + url);
    Properties paramProps = new Properties();
    paramProps.setProperty("oauth_token", this.requestToken);
    OAuthMessage response;
    String responseBody = null;
    try {
      response = sendRequest(paramProps, url, "GET");
      responseBody = response.readBodyAsString();
      if (DEBUG) Log.d(getClass().getName(), "responseBody: " + responseBody);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new ConnectionProblem();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    } catch (OAuthException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return responseBody;
  }

  @SuppressWarnings("unchecked")
  private OAuthMessage sendRequest(Map map, String url, String method) throws IOException, URISyntaxException, OAuthException, AccessDeniedException {
    if (DEBUG) Log.d(getClass().getName(), "YammerProxy.sendRequest");

    List<Map.Entry> params = new ArrayList<Map.Entry>();
    Iterator it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry p = (Map.Entry) it.next();
      params.add(new OAuth.Parameter((String)p.getKey(), (String)p.getValue()));
    }

    try {        
      accessor.tokenSecret = this.tokenSecret;

      // It seems that we need to send the auth header for Yammer at least, when submitting POST requests
      if ( method == "POST" ) {
        accessor.consumer.setProperty(OAuthClient.PARAMETER_STYLE, "AUTHORIZATION_HEADER");    			
      } else {
        accessor.consumer.setProperty(OAuthClient.PARAMETER_STYLE, "QUERY_STRING");    			
      }
      if (DEBUG) Log.d(getClass().getName(), "Invoking: " + url + " params: "+params.toString());
      return client.invoke(accessor, method, url, params);
    } catch (OAuthProblemException e) {
      int statusCode = e.getHttpStatusCode();
      if (DEBUG) Log.d(getClass().getName(), "HTTP status code: "+statusCode);
      if (302 == statusCode) { 
        if (DEBUG) Log.d(getClass().getName(), (String)e.getParameters().get(HttpResponseMessage.LOCATION));
        throw e;
      } else if (401 == statusCode) {
        throw (AccessDeniedException)new AccessDeniedException().fillInStackTrace();
      }
    }
    // It seems an error occured
    return null;
  }
}
