package com.jvmcode.philips.hue;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.jvmcode.philips.hue.HueHubComm.RM.*;
import static com.jvmcode.philips.hue.HueLightBulb.ColorMode.*;

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

	public static List<HueHub> discover() {
		return HueHubComm.discover();
	}

	final HueHubComm comm;

	String UDN;
	String username;
	boolean authenticated = false;
	boolean initialSyncDone = false;

	String deviceType = getClass().getName();

	String name;
	Map<Integer, HueLightBulb> lights = new TreeMap<>();

	/**
	 * Get a HueHub for a known IP Address without using {@link #discover()}.
	 *
	 * @param address The IP Adress of the Philips Hue Bridge
	 * @param username A username to authenticate with. May be null (in which case one will be generated on successful authentication)
	 */
	public HueHub(InetAddress address, String username) {
		this(constructBaseUrlFor(address), username);
	}

	HueHub(URL baseUrl, String username) {
		this.username = username;
		this.comm = new HueHubComm(baseUrl);
	}

	public URL getBaseUrl() {
		return comm.baseUrl;
	}

	public String getUDN() {
		return UDN;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		if(username!=null && !username.matches("\\s*[-\\w]{10,40}\\s*")) {
			throw new IllegalArgumentException("A username must be 10-40 characters long and may only contain the characters -,_,a-b,A-B,0-9");
		}
		authenticated &= equalEnough(this.username, username);
		this.username = username==null ? username : username.trim();
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public boolean authenticate(boolean waitForGrant) {
		return authenticate(username, waitForGrant);
	}

	public String getName() {
		checkAuthAndSync();
		return name;
	}

	public void setName(String name) {
		if(name==null || name.trim().length()<4 || name.trim().length()>16) {
			throw new IllegalArgumentException("Name (without leading or trailing whitespace) has to be 4-16 characters long");
		}
		checkedSuccessRequest(PUT, "/config", JO().key("name").value(name));
		this.name = name;
	}

	public Collection<HueLightBulb> getLights() {
		checkAuthAndSync();
		return lights.values();
	}

	public HueLightBulb getLight(int id) {
		checkAuthAndSync();
		return lights.get(id);
	}

	public Set<Integer> getLightIds() {
		checkAuthAndSync();
		return lights.keySet();
	}

	public boolean authenticate(String usernameToTry, boolean waitForGrant) {
		if(usernameToTry!=null && !usernameToTry.matches("\\s*[-\\w]{10,40}\\s*")) {
			throw new IllegalArgumentException("A username must be 10-40 characters long and may only contain the characters -,_,a-b,A-B,0-9");
		}

		if(!isAuthenticated() || !equalEnough(username, usernameToTry)) {
			// if we have an usernameToTry then check that first whether it already exists
			// I just don't get why a "create new user" request for an existing user results in the same 101 error as when the user does not exist.
			// But for this reason this additional preliminary request is necessary.
			if(!equalEnough(null, usernameToTry)) {
				try {
					completeSync(usernameToTry);
					authenticated = true;
				} catch(HueCommException e) {
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
						if(usernameToTry!=null && usernameToTry.trim().length()>=10) {
							jsonWriter.key("username").value(usernameToTry.trim());
						}
						// use comm directly here because the user is not currently set
						response = comm.request(POST, "api", jsonWriter.endObject().toString()).get(0);
					} catch(IOException e) {
						log.log(Level.WARNING, "IOException on create user request", e);
					}
					final JSONObject success = response.optJSONObject("success");
					if(success!=null && success.has("username")) {
						username = success.getString("username");
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

		if(isAuthenticated() && !initialSyncDone) {
			completeSync(username);
		}

		return isAuthenticated();
	}

	@Override
	public String toString() {
		return (initialSyncDone ? getName()+"@" : "<Unsynced Hue Hub>@")+getBaseUrl()+"#"+getUDN();
	}

	private void completeSync(String username) {
		try {
			final List<JSONObject> response = comm.request(GET, "api/" + username.trim(), "");
			if(response.size()>0) {
				final JSONObject datastore = response.get(0);
				if(datastore.has("error")) {
					throw new HueCommException(datastore.getJSONObject("error"));
				}
				if(datastore.has("config") && datastore.has("lights")) {
					parseConfig(datastore.getJSONObject("config"));
					parseLights(datastore.getJSONObject("lights"));
					this.setUsername(username);
					initialSyncDone = true;
				} else {
					throw new HueCommException("Incomplete response. Missing config and/or lights");
				}
			} else {
				throw new HueCommException("Empty response");
			}
		} catch(IOException e) {
			throw new HueCommException(e);
		}
	}

	private void parseConfig(JSONObject config) {
		name = config.getString("name");
	}

	private void parseLights(JSONObject lightsJson) {
		final Iterator<?> keys = lightsJson.keys();
		while(keys.hasNext()) {
			Object key = keys.next();
			try {
				Integer id = Integer.parseInt((String)key);
				HueLightBulb light = lights.get(id);
				if(light==null) {
					light = new HueLightBulb(this, id);
					lights.put(id, light);
				}

				final JSONObject lightJson = lightsJson.getJSONObject((String)key);
				light.name = lightJson.getString("name");

				final JSONObject state = lightJson.getJSONObject("state");
				light.on = state.getBoolean("on");
				light.brightness = state.getInt("bri");
				light.hue = state.getInt("hue");
				light.saturation = state.getInt("sat");
				light.ciex = state.getJSONArray("xy").getDouble(0);
				light.ciey = state.getJSONArray("xy").getDouble(1);
				light.colorTemperature = state.getInt("ct");
				light.colorMode = new HueLightBulb.ColorMode[]{HS,XY,CT}[Arrays.asList("hs","xy","ct").indexOf(state.getString("colormode").toLowerCase())];

			} catch(Exception e) {
				throw new HueCommException("Lights result parsing failed. Probably some unexpected format?", e);
			}
		}
	}

	List<JSONObject> checkedSuccessRequest(HueHubComm.RM method, String userPath, JSONWriter json) {
		final List<JSONObject> response = request(method, userPath, json);
		if(!response.get(0).has("success")) {
			throw new HueCommException(response.get(0).getJSONObject("error"));
		}
		return response;
	}

	List<JSONObject> request(HueHubComm.RM method, String userPath, JSONWriter json) {
		checkAuthAndSync();
		try {
			return comm.request(method, "api/" + username.trim() +userPath, json.toString());
		} catch(IOException e) {
			throw new HueCommException(e);
		}
	}

	void checkAuthAndSync() {
		if(!isAuthenticated()) {
			throw new IllegalStateException("Need to authenticate first.");
		}
		if(!initialSyncDone) {
			completeSync(username);
		}
	}

	/**
	 * Helper method to shorten creation of a JSONObject String.
	 * @return A JSONStringer with an object already 'open' and auto-object-end on a call to toString()
	 */
	private static JSONStringer JO() {
		return new JSONStringer() {
			boolean endObjectDone = false;
			{ object(); }
			@Override
			public String toString() {
				if(!endObjectDone) {
					endObject();
				}
				return super.toString();
			}
		};
	}

	private static boolean equalEnough(String a, String b) {
		// two strings are equal if either both are null/effectively empty or both represent the same string.
		return (a==null || a.trim().equals("")) && (b==null || b.trim().equals("")) || a!=null && b!=null && a.equals(b);
	}

	private static URL constructBaseUrlFor(InetAddress address) {
		try {
			return new URL("http://" +address.getHostAddress() +"/");
		} catch(MalformedURLException e) {
			throw new IllegalArgumentException("Can not construct http URL from the given address", e);
		}
	}
}
