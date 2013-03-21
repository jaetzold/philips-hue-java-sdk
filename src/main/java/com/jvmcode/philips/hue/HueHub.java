package com.jvmcode.philips.hue;

import com.jvmcode.networking.SimpleServiceDiscovery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Stephan Jaetzold <p><small>Created at 20.03.13, 15:10</small>
 */
public class HueHub {
	Logger log = Logger.getLogger(this.getClass().getName());
	{
		log.setLevel(Level.FINER);
	}
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

	String UDN;
	final URL baseUrl;
	String authToken;
	boolean authenticated = false;

	String deviceType = getClass().getName();

	public HueHub(InetAddress address, String authToken) {
		this(constructBaseUrlFor(address), authToken);
	}

	public HueHub(URL baseUrl, String authToken) {
		this.baseUrl = baseUrl;
		this.authToken = authToken;
	}

	public URL getBaseUrl() {
		return baseUrl;
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
					final List<JSONObject> response = request(RM.GET, "api/" + authTokenToTry.trim(), "");
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
						response = request(RM.POST, "api", jsonWriter.endObject().toString()).get(0);
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

	private List<JSONObject> request(RM method, String fullPath, JSONObject json) throws IOException {
		return request(method, fullPath, json.toString());
	}

	private List<JSONObject> request(RM method, String fullPath, String json) throws IOException {
		final URL url = new URL(baseUrl, fullPath);
		log.fine("Request to " +method +" " + url +(json!=null && json.length()>0 ? ": "+json : ""));
		final URLConnection connection = url.openConnection();
		if(connection instanceof HttpURLConnection) {
			HttpURLConnection httpConnection = (HttpURLConnection)connection;
			httpConnection.setRequestMethod(method.name());
			if(json!=null && json.length()>0) {
				httpConnection.setDoOutput(method==RM.POST);
				httpConnection.getOutputStream().write(json.getBytes(REQUEST_CHARSET));
			}
		} else {
			throw new IllegalStateException("The current baseUrl \"" +baseUrl +"\" is not http?");
		}

		final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), REQUEST_CHARSET));
		StringBuilder builder = new StringBuilder();
		String line;
		while((line = reader.readLine())!=null) {
			builder.append(line).append('\n');
		}
		log.fine("Response from " + method + " " + url + ": " + builder);
		final String jsonString = builder.toString();
		List<JSONObject> result = new ArrayList<>();
		if(jsonString.trim().startsWith("{")) {
			result.add(new JSONObject(jsonString));
		} else {
			final JSONArray array = new JSONArray(jsonString);
			for(int i=0; i<array.length(); i++) {
				result.add(array.getJSONObject(i));
			}
		}
		return result;
	}

	private enum RM {
		GET,POST,PUT,DELETE
	}

	private static boolean equalEnough(String a, String b) {
		// two strings are equal if either both are null/effectively empty or both represent the same string.
		return (a==null || a.trim().equals("")) && (b==null || b.trim().equals("")) || a!=null && b!=null && a.equals(b);
	}

	private static final String REQUEST_CHARSET = "UTF-8";

	public static List<HueHub> discover() {
		final Logger log = Logger.getLogger(HueHub.class.getName());
		final SimpleServiceDiscovery serviceDiscovery = new SimpleServiceDiscovery();
		int attempted = 0;
		int maxAttempts = Math.min(4, Math.max(1, discoveryAttempts));
		Map<String, URL> foundHubs = new HashMap<>();
		// if nothing is found the first time try up to maxAttempts times with increasing timeouts
		while(foundHubs.isEmpty() && attempted<maxAttempts) {
			serviceDiscovery.setSearchMx(1+attempted);
			serviceDiscovery.setSocketTimeout(500 + attempted*1500);
			final List<? extends SimpleServiceDiscovery.Response> responses =
					serviceDiscovery.discover(SimpleServiceDiscovery.SEARCH_TARGET_ROOTDEVICE);
			try {
				for(SimpleServiceDiscovery.Response response : responses) {
					String urlBase = null;
					final String usn = response.getHeader("USN");
					if(usn!=null && usn.matches("uuid:[-\\w]+")) {
						if(!foundHubs.containsKey(usn)) {
							final String server = response.getHeader("SERVER");
							if(server!=null && server.contains("IpBridge")) {
								final String location = response.getHeader("LOCATION");
								if(location!=null && location.endsWith(".xml")) {
									DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
									DocumentBuilder db = dbf.newDocumentBuilder();
									Document doc = db.parse(new URL(location).openStream());
									final NodeList modelNames = doc.getElementsByTagName("modelName");
									for(int i=0; i<modelNames.getLength(); i++) {
										final Node item = modelNames.item(i);
										if(item.getParentNode().getNodeName().equals("device")
										   && item.getTextContent().matches("(?i).*philips\\s+hue\\s+bridge.*")) {
											final NodeList urlBases = doc.getElementsByTagName("URLBase");
											if(urlBases.getLength()>0) {
												urlBase = urlBases.item(0).getTextContent();
												break;
											}
										}
									}
								}
							}
						}
					}
					if(urlBase!=null) {
						foundHubs.put(usn, new URL(urlBase));
					}
				}
			} catch(Exception e) {
				lastDiscoveryException = e;
				log.log(Level.INFO, "Exception when dicovering devices", e);
			}
			attempted++;
		}
		List<HueHub> result = new ArrayList<>();
		for(Map.Entry<String, URL> entry : foundHubs.entrySet()) {
			final HueHub hub = new HueHub(entry.getValue(), null);
			hub.UDN = entry.getKey();
			result.add(hub);
		}
		return result;
	}

	private static URL constructBaseUrlFor(InetAddress address) {
		try {
			return new URL("http://" +address.getHostAddress() +"/");
		} catch(MalformedURLException e) {
			throw new IllegalArgumentException("Can not construct http URL from the given address", e);
		}
	}
}
