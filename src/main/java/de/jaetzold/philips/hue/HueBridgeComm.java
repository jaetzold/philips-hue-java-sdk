package de.jaetzold.philips.hue;

import de.jaetzold.networking.SimpleServiceDiscovery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** @author Stephan Jaetzold <p><small>Created at 21.03.13, 15:06</small> */
class HueBridgeComm {
	Logger log = Logger.getLogger(this.getClass().getName());

	final URL baseUrl;
	private static final String REQUEST_CHARSET = "UTF-8";
	static enum RM {
		GET,POST,PUT,DELETE
	}

	HueBridgeComm(URL baseUrl) {
		this.baseUrl = baseUrl;
	}

	List<JSONObject> request(RM method, String fullPath, JSONObject json) throws IOException {
		return request(method, fullPath, json.toString());
	}

	List<JSONObject> request(RM method, String fullPath, String json) throws IOException {
		final URL url = new URL(baseUrl, fullPath);
		log.fine("Request to " +method +" " + url +(json!=null && json.length()>0 ? ": "+json : ""));
		final URLConnection connection = url.openConnection();
		if(connection instanceof HttpURLConnection) {
			HttpURLConnection httpConnection = (HttpURLConnection)connection;
			httpConnection.setRequestMethod(method.name());
			if(json!=null && json.length()>0) {
				if(method==RM.GET || method==RM.DELETE) {
					throw new HueCommException("Will not send json content for request method " +method);
				}
				httpConnection.setDoOutput(true);
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

	static List<HueBridge> discover() {
		final Logger log = Logger.getLogger(HueBridge.class.getName());
		final SimpleServiceDiscovery serviceDiscovery = new SimpleServiceDiscovery();
		int attempted = 0;
		int maxAttempts = Math.min(4, Math.max(1, HueBridge.discoveryAttempts));
		Map<String, URL> foundBriges = new HashMap<>();
		// if nothing is found the first time try up to maxAttempts times with increasing timeouts
		while(foundBriges.isEmpty() && attempted<maxAttempts) {
			serviceDiscovery.setSearchMx(1+attempted);
			serviceDiscovery.setSocketTimeout(500 + attempted*1500);
			final List<? extends SimpleServiceDiscovery.Response> responses =
					serviceDiscovery.discover(SimpleServiceDiscovery.SEARCH_TARGET_ROOTDEVICE);
			try {
				for(SimpleServiceDiscovery.Response response : responses) {
					String urlBase = null;
					final String usn = response.getHeader("USN");
					if(usn!=null && usn.matches("uuid:[-\\w]+")) {
						if(!foundBriges.containsKey(usn)) {
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
						foundBriges.put(usn, new URL(urlBase));
					}
				}
			} catch(Exception e) {
				HueBridge.lastDiscoveryException = e;
				log.log(Level.INFO, "Exception when dicovering devices", e);
			}
			attempted++;
		}
		List<HueBridge> result = new ArrayList<>();
		for(Map.Entry<String, URL> entry : foundBriges.entrySet()) {
			final HueBridge bridge = new HueBridge(entry.getValue(), null);
			bridge.UDN = entry.getKey();
			result.add(bridge);
		}
		return result;
	}
}
