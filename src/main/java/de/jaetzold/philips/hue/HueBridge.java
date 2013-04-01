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
 * Represents the properties and abilities of a Philips hue Bridge.
 * Bridges on the local network can be discovered using
 * <code>{@link #discover() HueBridge.discover()}</code>
 * which does a UPnP search for any bridges.
 * <p>
 *     In order to use the lights it is necessary to call {@link #authenticate(boolean)}.
 *     It checks that a username provided with {@link #setUsername(String)}
 *     is allowed for the bridge. If not and the method is called with <code>true</code> as parameter
 *     it waits for up to 30 seconds for the link button on the bridge to be pressed so that access is granted for a new user.
 *     It is not necessary to provide a name for the new user, a random one will be generated then.
 * </p>
 * <p>
 *     Save the username along with the UDN obtained via {@link #getUDN()} to later use it again with the same bridge device.
 * </p>
 * <p>
 *     An authenticated bridge provides the lights and groups via corresponding get methods and can be told to
 *     initiate a search for new lights.
 * </p>
 * <p>
 *     Any problem communicating with the bridge device will result in a {@link HueCommException} to be thrown which carries
 *     the error json with more information.
 * </p>
 *
 * @see HueLightBulb
 * @see HueLightGroup
 * @see HueVirtualLightGroup
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

	/**
	 * Do a UPnP search for Philips hue bridges on the local network.
	 * The returned bridges are not authenticated and not synced.
	 * So all information at that point will be the base URL and UPnP UDN.
	 *
	 * @return All Philips hue bridges discovered on the local network. If none are found an empty list is returned.
	 */
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
	@SuppressWarnings("UnusedDeclaration")
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

	/**
	 * The base URL of the Philips hue bridge REST API this bridge instance connects to.
	 *
	 * @return The base URL of the Philips hue bridge REST API this bridge instance connects to.
	 */
	public URL getBaseUrl() {
		return comm.baseUrl;
	}

	/**
	 * The UPnP unique device identifier string of the bridge.
	 * It can be used to identify the same bridge device later even if its IP changes due to some other configuration.
	 *
	 * @return The UPnP unique device identifier string of the bridge.
	 */
	public String getUDN() {
		return UDN;
	}

	/**
	 * @return The username that is used to connect to the bridge device. Or null if this bridge is not authenticated and no username was explicitly set.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Set the username to use with this bridge.
	 * If this is different from the previous username the bridge needs to be authenticated again.
	 *
	 * @see #authenticate(boolean)
	 *
	 * @param username A username to authenticate with. May be null (in which case one will be generated on successful authentication)
	 */
	public void setUsername(String username) {
		if(username!=null && !username.matches("\\s*[-\\w]{10,40}\\s*")) {
			throw new IllegalArgumentException("A username must be 10-40 characters long and may only contain the characters -,_,a-b,A-B,0-9");
		}
		authenticated &= equalEnough(this.username, username);
		this.username = username==null ? username : username.trim();
	}

	/**
	 * Being authenticated means the current username has already been checked to be accepted by the bridge API found at {@link #getBaseUrl()}.
	 * @return true if the current username is allowed by the bridge device.
	 */
	public boolean isAuthenticated() {
		return authenticated;
	}

	/**
	 * Check for the current username to be allowed by the bridge device. Or generate and use a random user if none has been set with
	 * {@link #setUsername(String)}. If the parameter <code>waitForGrant</code> is true and the currently set username is not allowed
	 * by the bridge wait for up to 30 seconds for the link button on the bridge to be pressed to get an allowed username.
	 *
	 * @see #authenticate(String, boolean)
	 *
	 * @param waitForGrant true if the method should wait for up to 30 seconds for the link button to be pressed
	 * @return true if authentication was successful.
	 */
	public boolean authenticate(boolean waitForGrant) {
		return authenticate(username, waitForGrant);
	}

	/**
	 * The name that has been assigned to the bridge device.
	 * @return The name that has been assigned to the bridge device.
	 */
	public String getName() {
		checkAuthAndSync();
		return name;
	}

	/**
	 * Assign a new name to the bridge device. This name is actually sent to and stored on the device.
	 * It must be between 4 and 16 characters long.
	 * <p>See <a href="http://developers.meethue.com/4_configurationapi.html#43_modify_configuration">Philips hue API, Section 4.3</a> for further reference.</p>
	 *
	 * @param name The new name of the bridge device.
	 */
	public void setName(String name) {
		if(name==null || name.trim().length()<4 || name.trim().length()>16) {
			throw new IllegalArgumentException("Name (without leading or trailing whitespace) has to be 4-16 characters long");
		}
		checkedSuccessRequest(PUT, "/config", JO().key("name").value(name));
		this.name = name;
	}

	/**
	 * Provide a collection of all hue lights that are currently known by the bridge.
	 * On adding completely new lights call {@link #searchForNewLights()} so that the bridge device finds and connects those new lights.
	 *
	 * @see #getLight(Integer)
	 * @see #getLightIds()
	 *
	 * @return A collection of all hue lights that are currently known by the bridge.
	 */
	public Collection<? extends HueLightBulb> getLights() {
		checkAuthAndSync();
		return lights.values();
	}

	/**
	 * Get a light with a known id. Either saved from earlier or from the ids returned by {@link #getLightIds()}.
	 *
	 * @param id the id of the light to return
	 *
	 * @return A {@link HueLightBulb} with the given id or null if none with that id is known.
	 */
	public HueLightBulb getLight(Integer id) {
		checkAuthAndSync();
		return lights.get(id);
	}

	/**
	 * The ids of all the lights known to this bridge device.
	 *
	 * @return The ids of all the lights known to this bridge device.
	 */
	public Set<Integer> getLightIds() {
		checkAuthAndSync();
		return Collections.unmodifiableSet(lights.keySet());
	}

	/**
	 * Provide a collection of all groups that are currently defined on the bridge.
	 *
	 * @see #getGroup(Integer)
	 * @see #getGroupIds()
	 *
	 * @return A collection of all groups that are currently defined on the bridge.
	 */
	public Collection<? extends HueLightGroup> getGroups() {
		checkAuthAndSync();
		return groups.values();
	}

	/**
	 * Get a group with a known id. Either saved from earlier or from the ids returned by {@link #getGroupIds()}.
	 *
	 * @param id the id of the group to return
	 *
	 * @return A {@link HueLightGroup} with the given id or null if none with that id exists.
	 */
	public HueLightGroup getGroup(Integer id) {
		checkAuthAndSync();
		return groups.get(id);
	}

	/**
	 * The ids of all the groups defined on this bridge device.
	 *
	 * @return The ids of all the groups defined on this bridge device.
	 */
	public Set<Integer> getGroupIds() {
		checkAuthAndSync();
		return Collections.unmodifiableSet(groups.keySet());
	}

	/**
	 * Provide a collection of all {@link HueVirtualLightGroup} instances that are currently defined on this bridge API instance.
	 *
	 * @see HueVirtualLightGroup
	 * @see #getVirtualGroup(Integer)
	 * @see #getVirtualGroupIds()
	 *
	 * @return A collection of all virtual groups that are currently defined on this bridge API instance.
	 */
	public Collection<HueVirtualLightGroup> getVirtualGroups() {
		return virtualGroups.values();
	}

	/**
	 * Get a virtual group with a known id. Either saved from earlier or from the ids returned by {@link #getVirtualGroupIds()}.
	 *
	 * @param id the id of the virtual group to return
	 *
	 * @return A {@link HueVirtualLightGroup} with the given id or null if none with that id is defined on this bridge API instance.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public HueVirtualLightGroup getVirtualGroup(Integer id) {
		return virtualGroups.get(id);
	}

	/**
	 * The ids of all the virtual groups defined on this bridge API instance.
	 *
	 * @return The ids of all the virtual groups defined on this bridge API instance.
	 */
	public Set<Integer> getVirtualGroupIds() {
		return virtualGroups.keySet();
	}

	/**
	 * Attempt to verify the given username is allowed to access the bridge instance.
	 * If the username is not allowed (or not given, meaning <code>null</code>) and <code>waitForGrant</code> is <code>true</code>,
	 * the method then waits for up to 30 seconds to be granted access to the bridge. This would be done by pressing the bridges button.
	 * If authentication succeeds, the username for which it succeeded is then saved and the method returns <code>true</code>.
	 *
	 * <p>See <a href="http://developers.meethue.com/4_configurationapi.html#41_create_user">Philips hue API, Section 4.1</a> for further reference.</p>
	 *
	 * @see #authenticate(boolean)
	 *
	 * @param usernameToTry a username to authenticate with or null if a new one should be generated by the bridge if access is granted through pressing the hardware button.
	 * @param waitForGrant if true, this method blocks for up to 30 seconds or until access to the bridge is allowed, whichever comes first.
	 *
	 * @return true, if this bridge API instance has now a username that is verified to be allowed to access the bridge device.
	 */
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

	// *****************************************
	// Implementation internal methods
	// *****************************************

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
