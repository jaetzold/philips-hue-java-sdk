package com.jvmcode.philips.hue;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.jvmcode.philips.hue.HueHubComm.RM.*;

/**
 *
 * @author Stephan Jaetzold <p><small>Created at 20.03.13, 15:10</small>
 */
public class HueHub {
	Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * If {@link #discover()} does not return what is expected this field might help track down the cause.
	 * But this should be rare and probably means there is a bug in generating or parsing the discovery responses somewhere.
	 * It is unusual not to throw an exception but instead remember it in a variable, but it would otherwise not be possible to
	 * be able to discover hubs if e.g. some completely unrelated UPnP device causes an exception with its response.
	 */
	public static Exception lastDiscoveryException;
	/**
	 * If {@link #discover()} should return quicker if there are no hubs found, set this to a lower value.
	 * Might not find existing hubs then if the network is very slow. 1-4 is acceptable.
	 */
	public static int discoveryAttempts = 3;
	final HueHubComm comm;

	String UDN;
	String authToken;
	boolean authenticated = false;

	String deviceType = getClass().getName();

	public HueHub(InetAddress address, String authToken) {
		this(constructBaseUrlFor(address), authToken);
	}

	public HueHub(URL baseUrl, String authToken) {
		this.authToken = authToken;
		this.comm = new HueHubComm(baseUrl);
	}

	public URL getBaseUrl() {
		return comm.baseUrl;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		if(authToken!=null && !authToken.matches("\\s*[-\\w]{10,40}\\s*")) {
			throw new IllegalArgumentException("A username must be 10-40 characters long and may only contain the characters -,_,a-b,A-B,0-9");
		}
		authenticated &= equalEnough(this.authToken, authToken);
		this.authToken = authToken==null ? authToken : authToken.trim();
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public boolean authenticate(boolean waitForGrant) {
		return authenticate(authToken, waitForGrant);
	}

	public boolean authenticate(String authTokenToTry, boolean waitForGrant) {
		if(!isAuthenticated() || !equalEnough(authToken, authTokenToTry)) {
			// if we have an authTokenToTry then check that first whether it already exists
			// I just don't get why a "create new user" request for an existing user results in the same 101 error as when the user does not exist.
			// But for this reason this additional preliminary request is necessary.
			if(!equalEnough(null, authTokenToTry)) {
				try {
					final List<JSONObject> response = comm.request(GET, "api/" + authTokenToTry.trim(), "");
					if(response.size()>0 && !response.get(0).has("error")) {
						authToken = authTokenToTry;
						authenticated = true;
					}
				} catch(IOException e) {
					log.log(Level.WARNING, "IOException on check grant request", e);
				}
			}

			if(!isAuthenticated()) {
				long start = System.currentTimeMillis();
				int waitSeconds = 30;
				do {
					JSONObject response = new JSONObject();
					try {
						final JSONWriter jsonWriter = new JSONStringer().object()
								.key("devicetype").value(deviceType);
						if(authTokenToTry!=null && authTokenToTry.trim().length()>=10) {
							jsonWriter.key("username").value(authTokenToTry.trim());
						}
						response = comm.request(POST, "api", jsonWriter.endObject().toString()).get(0);
					} catch(IOException e) {
						log.log(Level.WARNING, "IOException on create user request", e);
					}
					final JSONObject success = response.optJSONObject("success");
					if(success!=null && success.has("username")) {
						authToken = success.getString("username");
						authenticated = true;
						waitForGrant = false;
					} else {
						final JSONObject error = response.optJSONObject("error");
						if(error!=null && error.has("type")) {
							if(error.getInt("type")!=101) {
								log.warning("Got unexpected error on create user: " +error);
								waitForGrant = false;
							}
						}
					}
					if(waitForGrant) {
						if(System.currentTimeMillis()-start>waitSeconds*1000) {
							waitForGrant = false;
						} else {
							try {
								Thread.sleep(900 +Math.round(Math.random()*100));
							} catch(InterruptedException e) {
							}
						}
					}
				} while(waitForGrant);
			}
		}
		return isAuthenticated();
	}


	private static boolean equalEnough(String a, String b) {
		// two strings are equal if either both are null/effectively empty or both represent the same string.
		return (a==null || a.trim().equals("")) && (b==null || b.trim().equals("")) || a!=null && b!=null && a.equals(b);
	}

	public static List<HueHub> discover() {
		return HueHubComm.discover();
	}

	private static URL constructBaseUrlFor(InetAddress address) {
		try {
			return new URL("http://" +address.getHostAddress() +"/");
		} catch(MalformedURLException e) {
			throw new IllegalArgumentException("Can not construct http URL from the given address", e);
		}
	}
}
