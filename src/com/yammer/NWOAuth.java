package com.yammer;

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

public class NWOAuth {
    
    /**
     * Properties for service we are trying to authenticate against.
     * 
     * Yammer properties:
     */
	String applicationName = "Yowl";
	String consumerKey = "oiKSxL1n0t2BPV33hWwnmg"; // Yowl!
    String consumerSecret = "u0wFcKwQADA00eR9OAGF5DVSJGuiPsbFvFZIvp6lmyA"; // Yowl!
    String callbackUrl = "https://nullwire.com/yowl/callback";
    String reqUrl = "https://www.yammer.com/oauth/request_token";
    String authzUrl = "https://www.yammer.com/oauth/authorize";
    String accessUrl = "https://www.yammer.com/oauth/access_token";

    /*
	String applicationName = "Android Yammer Test1";
	String consumerKey = "01vEsltYhVHhNy4hByTBQ";
    String consumerSecret = "5sddkWHnf8wRwc6UagVIWc4vasP0ogHED4FKV1HT8I";
    String callbackUrl = "https://nullwire.com/yammer/callback";
    String reqUrl = "https://www.yammer.com/oauth/request_token";
    String authzUrl = "https://www.yammer.com/oauth/authorize";
    String accessUrl = "https://www.yammer.com/oauth/access_token";
    */
    
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
        
   public NWOAuth() {
	   if (G.DEBUG) Log.d("OAuth", "OAuth::OAuth");
	   // Create OAuth accessor
	   this.provider = new OAuthServiceProvider(reqUrl, authzUrl, accessUrl);
       this.consumer = new OAuthConsumer(callbackUrl, consumerKey, consumerSecret, provider);        
       this.accessor = new OAuthAccessor(consumer);
       this.client = new OAuthClient(new HttpClient4());
    }
    
   	/**
   	 * Reset the OAuth library
   	 */
   	public void reset() {
 	   this.provider = new OAuthServiceProvider(reqUrl, authzUrl, accessUrl);
       this.consumer = new OAuthConsumer(callbackUrl, consumerKey, consumerSecret, provider);        
       this.accessor = new OAuthAccessor(consumer);
       this.client = new OAuthClient(new HttpClient4());
       this.requestToken = null;
       this.tokenSecret = null;
   	}
   
    /**
     * Request a OAuth "Request Token".
     * @throws NWOAuthConnectionProblem 
     */
    public void getRequestToken() throws NWOAuthConnectionProblem {    	
    	if (G.DEBUG) Log.d("OAuth", "NWOAuth::getRequestToken");

    	if ( accessor == null ) {
    		if ( G.DEBUG ) Log.e("OAuth", "accessor not available (yet!)");
    		return;
    	}   
    	
        try {
            client.getRequestToken(accessor);
        } catch (java.io.IOException e) {
        	if (G.DEBUG) Log.e("yammerApp", "IOException: " + e.toString());    	
			throw new NWOAuthConnectionProblem();
        } catch (OAuthException e) {
        	if (G.DEBUG) Log.e("yammerApp", "OAuthException: " + e.toString());
			throw new NWOAuthConnectionProblem();
        } catch (Exception e) {
            // Do something
        	if (G.DEBUG) Log.e("yammerApp", "An unknown error occured: " + e.toString());
			throw new NWOAuthConnectionProblem();
		}
        // We should now have a request token and a token secret
        this.requestToken = accessor.requestToken;
        this.tokenSecret = accessor.tokenSecret;
        
        if (G.DEBUG) Log.d("OAuth", "Request token: " + this.requestToken);
        if (G.DEBUG) Log.d("OAuth", "Request token secret: " + this.tokenSecret);    
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

    public void enableApplication(String callbackToken) throws NWOAuthAccessDeniedException {
		if (G.DEBUG_OAUTH) Log.d("OAuth", "NWOAuth::enableApplication");
    	Properties paramProps = new Properties();
    	paramProps.setProperty("oauth_token", this.requestToken);
    	try {
    		if (G.DEBUG_OAUTH) Log.d("OAuth", "Invoking: " + this.accessUrl+"?callback_token="+callbackToken);
			OAuthMessage response = sendRequest( paramProps, this.accessUrl+"?callback_token="+callbackToken, "GET");
			// Store the access token secret
			this.requestToken =  response.getParameter("oauth_token");
			this.tokenSecret =  response.getParameter("oauth_token_secret");
			if (G.DEBUG_OAUTH) Log.d("OAuth", "oauth_token: " + this.requestToken);
			if (G.DEBUG_OAUTH) Log.d("OAuth", "oauth_token_secret: " + this.tokenSecret);
    	} catch (IOException e) {
			e.printStackTrace();
			if (G.DEBUG_OAUTH) Log.d("OAuth", "IOException");
		} catch (URISyntaxException e) {
			e.printStackTrace();
			if (G.DEBUG_OAUTH) Log.d("OAuth", "URISyntaxException");
		} catch (OAuthProblemException e) {
			if (G.DEBUG_OAUTH) Log.d("OAuth", "HTTP status code: "+e.getHttpStatusCode());
			// Check if this is a redirect
			if (e.getHttpStatusCode() == 302) {
				if (G.DEBUG_OAUTH) Log.d( "OAuth", (String) e.getParameters().get(HttpResponseMessage.LOCATION) );
			}
		} catch (OAuthException e) {
			e.printStackTrace();
			if (G.DEBUG) Log.d("OAuth", "OAuthException");
		}
    }
    
    /**
     * Request "Access Token".
     * @throws NWOAuthAccessDeniedException 
     */
    public String authorizeUser() throws NWOAuthAccessDeniedException {
    	if (G.DEBUG) Log.d("OAuth", "NWOAuth::getAccessToken");
    	
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
			if (G.DEBUG_OAUTH) Log.d("OAuth", "HTTP status code: "+e.getHttpStatusCode());
        	// Try to retrieve the URL that resultet in the "error"
			// We need to do this because the OAuth library creates the URL being queried
			// with signatures and all, so we let the OAuth library create the URL, try
			// to do the request and then if it fails return the URL to let us try to 
			// request it with the browser.
        	URL calledUrl = (URL)e.getParameters().get("URL");
        	if (G.DEBUG_OAUTH) Log.d("OAuth", "Called URL: "+calledUrl);
        	responseUrl = calledUrl.toString();        	
			// Check if this is a redirect
			if (e.getHttpStatusCode() == 302) {
				if (G.DEBUG_OAUTH) Log.d("OAuth", "302 Location: " + (String) e.getParameters().get(HttpResponseMessage.LOCATION));
			}
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (G.DEBUG_OAUTH) Log.d("OAuth", "authorizeUserResulting URL: "+responseUrl);
		return responseUrl;
    }

    /**
     * Follow user with given user ID on Yammer
     * @param userId
     * @return
     * @throws NWOAuthAccessDeniedException
     * @throws NWOAuthConnectionProblem
     */
    public String followUser(long userId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "Following user: " + userId);    	
    	String responseBody = null;
    	String url = "https://www.yammer.com/api/v1/subscriptions/"; 
    	Properties paramProps = new Properties();
    	paramProps.setProperty("target_type", "user");
    	paramProps.setProperty("target_id", Long.toString(userId));
    	try {
			sendRequest(paramProps, url, "POST");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new NWOAuthConnectionProblem();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return responseBody;
    }
    
    public String unfollowUser(long userId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "Unfollowing user: " + userId);    	
    	String responseBody = null;
        String url = "https://www.yammer.com/api/v1/subscriptions/options?target_id=182108&target_type=user"; 
        //String url = "https://www.yammer.com/api/v1/subscriptions/"; 
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "URL: " + url);    	
    	Properties paramProps = new Properties();
    	paramProps.setProperty("target_type", "user");
    	paramProps.setProperty("target_id", Long.toString(userId));
    	paramProps.setProperty("oauth_token", this.requestToken);
    	try {
			sendRequest(paramProps, url, "DELETE");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new NWOAuthConnectionProblem();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return responseBody;    	
    }
    
    public String postResource(String url, String body, long messageId) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "Posting resource: " + url);    	
    	//OAuthMessage response;
    	String responseBody = null;
    	Properties paramProps = new Properties();
    	paramProps.setProperty("oauth_token", this.requestToken);
    	// Body will be sent in a body parameter
    	paramProps.setProperty("body", body);
    	if ( messageId != 0 ) {
    		paramProps.setProperty("replied_to_id", Long.toString(messageId));
    	}
    	try {
			sendRequest(paramProps, url, "POST");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new NWOAuthConnectionProblem();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
    	
    	return responseBody;
    }

    public String deleteResource(String url) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
    	if (G.DEBUG) Log.d("OAuth", "Deleting resource: " + url);    	
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
			throw new NWOAuthConnectionProblem();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
    	
    	return responseBody;
    }

    /**
     * Access protected resource
     * @throws NWOAuthAccessDeniedException 
     * @throws NWOAuthConnectionProblem 
     */
    public String accessResource(String url) throws NWOAuthAccessDeniedException, NWOAuthConnectionProblem {
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "Accessing resource: " + url);
    	Properties paramProps = new Properties();
    	paramProps.setProperty("oauth_token", this.requestToken);
    	OAuthMessage response;
    	String responseBody = null;
    	try {
			response = sendRequest(paramProps, url, "GET");
			responseBody = response.readBodyAsString();
			if (G.DEBUG_OAUTH) Log.d("OAuth", responseBody);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new NWOAuthConnectionProblem();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return responseBody;
    }
    
    @SuppressWarnings("unchecked")
	private OAuthMessage sendRequest(Map map, String url, String method) throws IOException, URISyntaxException, OAuthException, NWOAuthAccessDeniedException {
    	if (G.DEBUG_OAUTH) Log.d("OAuth", "NWOAuth::sendRequest");

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
        	return client.invoke(accessor, method, url, params);
        } catch ( OAuthProblemException e ) {
        	if (G.DEBUG_OAUTH) Log.d("OAuth", "HTTP status code: "+e.getHttpStatusCode());
        	int statusCode = e.getHttpStatusCode();
        	if ( statusCode == 302 ) { 
				Log.d( "OAuth", (String) e.getParameters().get(HttpResponseMessage.LOCATION) );
				throw e;
			} else if ( statusCode == 401 ) {
				// It seems access was denied
				NWOAuthAccessDeniedException accessDenied = new NWOAuthAccessDeniedException();
				throw accessDenied;
			}
        }
		// It seems an error occured
		return null;
    }
}
