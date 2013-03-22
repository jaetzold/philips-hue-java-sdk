package de.jaetzold.philips.hue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.jaetzold.philips.hue.HueBridgeComm.RM.*;

/**
 *
 * @author Stephan Jaetzold <p><small>Created at 20.03.13, 15:10</small>
 */
public class HueBridge {
	Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * If {@link #discover()} does not return what is expected this field might help track down the cause.
	 * But this should be rare and probably means there is a bug in generating or parsing the discovery responses somewhere.
	 * It is unusual not to throw an exception but instead remember it in a variable, but it would otherwise not be possible to
	 * be able to discover bridges if e.g. some completely unrelated UPnP device causes an exception with its response.
	 */
	public static Exception lastDiscoveryException;
	/**
	 * If {@link #discover()} should return quicker if there are no bridges found, set this to a lower value.
	 * Might not find existing bridges then if the network is very slow. 1-4 is acceptable.
	 */
	public static int discoveryAttempts = 3;

	public static List<HueBridge> discover() {
		return HueBridgeComm.discover();
	}

	final HueBridgeComm comm;

	String UDN;
	String username;
	boolean authenticated = false;
	boolean initialSyncDone = false;

	String deviceType = getClass().getName();

	String name;
	final Map<Integer, HueLightBulb> lights = new TreeMap<>();
	final Map<Integer, HueLightGroup> groups = new TreeMap<>();
	final Map<Integer, HueVirtualLightGroup> virtualGroups = new TreeMap<>();

	/**
	 * Get a HueBridge for a known IP Address without using {@link #discover()}.
	 *
	 * @param address The IP Adress of the Philips Hue Bridge
	 * @param username A username to authenticate with. May be null (in which case one will be generated on successful authentication)
	 */
	public HueBridge(InetAddress address, String username) {
		this(constructBaseUrlFor(address), username);
	}

	HueBridge(URL baseUrl, String username) {
		this.username = username;
		this.comm = new HueBridgeComm(baseUrl);
		// this group is implicit and always contains all lights of this bridge
		final HueLightGroup group = new HueLightGroup(this, 0, lights);
		group.name = "Implicit";
		groups.put(0, group);
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

	public Collection<? extends HueLightBulb> getLights() {
		checkAuthAndSync();
		return lights.values();
	}

	public HueLightBulb getLight(int id) {
		checkAuthAndSync();
		return lights.get(id);
	}

	public Set<Integer> getLightIds() {
		checkAuthAndSync();
		return Collections.unmodifiableSet(lights.keySet());
	}

	public Collection<? extends HueLightGroup> getGroups() {
		checkAuthAndSync();
		return groups.values();
	}

	public HueLightGroup getGroup(int id) {
		checkAuthAndSync();
		return groups.get(id);
	}

	public Set<Integer> getGroupIds() {
		checkAuthAndSync();
		return Collections.unmodifiableSet(groups.keySet());
	}

	public Collection<HueVirtualLightGroup> getVirtualGroups() {
		return virtualGroups.values();
	}

	public HueVirtualLightGroup getVirtualGroup(int id) {
		return virtualGroups.get(id);
	}

	public Set<Integer> getVirtualGroupIds() {
		return virtualGroups.keySet();
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
					e.printStackTrace();
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

	/**
	 * Starts a search for new lights.
	 * See <a href="http://developers.meethue.com/1_lightsapi.html#13_search_for_new_lights">Philips hue API, Section 1.3</a> for further reference.
	 */
	public void searchForNewLights() {
		checkedSuccessRequest(POST, "/lights", "");
	}

	private boolean scanActive = false;
	/**
	 * Returns all lights found at the last search for new lights.
	 * If the returned list is empty it can mean that no scan has been performed, no new lights have been discovered or a scan is currently active.
	 * Whether a scan was active at the last call to this method can be queried by calling {@link #isScanActive()}.
	 *
	 * See <a href="http://developers.meethue.com/1_lightsapi.html#12_get_new_lights">Philips hue API, Section 1.2</a> for further reference.
	 */
	public Collection<? extends HueLightBulb> getNewLights() {
		final List<JSONObject> response = request(GET, "/lights/new", "");
		final JSONObject lightsJson = response.get(0);
		final String lastscan = (String)lightsJson.remove("lastscan");
		scanActive = lastscan.equals("active");
		parseLights(lightsJson);
		final ArrayList<HueLightBulb> result = new ArrayList<>();
		for(Object key : lightsJson.keySet()) {
			Integer lightId = null;
			try {
				lightId = Integer.parseInt((String)key);
			} catch(Exception e) {
			}
			if(lightId!=null) {
				final HueLightBulb light = getLight(lightId);
				if(light==null) {
					throw new IllegalStateException("For some reason the new light is not available... probable bug?");
				}
				result.add(light);
			}
		}
		return result;
	}

	/**
	 * Whether a scan was active at the last call to {@link #getNewLights()}
	 */
	public boolean isScanActive() {
		return scanActive;
	}

	@Override
	public String toString() {
		return (initialSyncDone ? getName()+"@" : "<Unsynced Hue Bridge>@")+getBaseUrl()+"#"+getUDN();
	}

	private void completeSync(String username) {
		try {
			final List<JSONObject> response = comm.request(GET, "api/" + username.trim(), "");
			if(response.size()>0) {
				final JSONObject datastore = response.get(0);
				if(datastore.has("error")) {
					throw new HueCommException(datastore.getJSONObject("error"));
				}
				if(datastore.has("config") && datastore.has("lights") && datastore.has("groups")) {
					parseConfig(datastore.getJSONObject("config"));
					parseLights(datastore.getJSONObject("lights"));
					parseGroups(datastore.getJSONObject("groups"));
					this.setUsername(username);
					initialSyncDone = true;
				} else {
					throw new HueCommException("Incomplete response. Missing at least one of config/lights/groups");
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
				light.parseLight(lightJson);
			} catch(Exception e) {
				if(e instanceof HueCommException) {
					throw e;
				} else {
					throw new HueCommException("Lights result parsing failed. Probably some unexpected format?", e);
				}
			}
		}
	}

	private void parseGroups(JSONObject groupsJson) {
		final Iterator<?> keys = groupsJson.keys();
		while(keys.hasNext()) {
			Object key = keys.next();
			try {
				Integer id = Integer.parseInt((String)key);
				HueLightGroup group = groups.get(id);
				if(group==null) {
					group = new HueLightGroup(this, id);
					groups.put(id, group);
				}

				final JSONObject lightJson = groupsJson.getJSONObject((String)key);
				group.name = lightJson.getString("name");

				final JSONArray lightsArray = lightJson.getJSONArray("lights");
				for(int i=0; i<lightsArray.length(); i++) {
					Integer lightId = Integer.parseInt(lightsArray.getString(i));
					final HueLightBulb light = getLight(lightId);
					if(light==null) {
						//noinspection ThrowCaughtLocally
						throw new HueCommException("Can not find light with id " +lightId);
					} else {
						group.lights.put(lightId, light);
					}
				}

			} catch(Exception e) {
				if(e instanceof HueCommException) {
					throw e;
				} else {
					throw new HueCommException("Groups result parsing failed. Probably some unexpected format?", e);
				}
			}
		}
	}

	List<JSONObject> checkedSuccessRequest(HueBridgeComm.RM method, String userPath, Object json) {
		final List<JSONObject> response = request(method, userPath, json);
		for(JSONObject entry : response) {
			if(!entry.has("success")) {
				throw new HueCommException(entry.getJSONObject("error"));
			}
		}
		return response;
	}

	List<JSONObject> request(HueBridgeComm.RM method, String userPath, Object json) {
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
			{ object(); }
			@Override
			public String toString() {
				return writer.toString()+(mode!='d' ? "}" :"");
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
