package com.jvmcode.networking;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the Simple Service Discovery Protocol (SSDP) part that discovers devices.
 * The goal is to have no dependencies and provide device detection without the API user needing to know more than necessary about UPnP/SSDP.
 * So all values have defaults and a simple <code>new SimpleServiceDiscovery().discover()</code> suffices to get a list of all UPnP devices
 * in the current network.
 * To search only for UPnP root devices use <code>discover(SimpleServiceDiscovery.SEARCH_TARGET_ROOTDEVICE)</code>.
 *
 * @author Stephan Jaetzold <p><small>Created at 15.03.13, 16:34</small>
 */
@SuppressWarnings("UnusedDeclaration")
public class SimpleServiceDiscovery {
	static final int MULTICAST_PORT = 1900;
	static final String MULTICAST_ADDRESS = "239.255.255.250";
	static final String RESPONSE_MESSAGE_HEADER = "HTTP/1.1 200 OK\r\n";

	static final String USER_AGENT_OS = "Java";
	static final String USER_AGENT_OS_VERSION = System.getProperty("java.version");
	static final String CHARSET_NAME = "UTF-8";
	static final Pattern HEADER_PATTERN = Pattern.compile("([^\\p{Cntrl} :]+):\\s*(.*)");

	/**
	 * The search target to discover all devices.
	 */
	public static final String SEARCH_TARGET_ALL = "ssdp:all";
	/**
	 * The search target to discover only UPnP root devices.
	 */
	public static final String SEARCH_TARGET_ROOTDEVICE = "upnp:rootdevice";

	int searchMx = 2;	// maximum response wait time
	String searchTarget = SEARCH_TARGET_ALL;
	protected String userAgentProduct = SimpleServiceDiscovery.class.getName();
	protected String userAgentProductVersion = "0.1";

	final PrintStream debugLog;

	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1, Page 15, Paragraph 3
	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1.3.2, Page 30, Paragraph 4
	int ttl = 2;	// this SHOULD be configurable
	int socketTimeout = searchMx*1000+2000;

	public SimpleServiceDiscovery() {
		this(null);
	}

	/**
	 * @param debugLog a PrintStream where debug loggings with e.g. the received packet contents are sent to.
	 */
	public SimpleServiceDiscovery(PrintStream debugLog) {
		this.debugLog = debugLog;
	}

	/**
	 * The maximum time a device should delay sending its answer in seconds.
	 */
	public int getSearchMx() {
		return searchMx;
	}

	/**
	 * @param searchMx The maximum time a device should delay sending its answer in seconds. Defaults to 2.
	 */
	public void setSearchMx(int searchMx) {
		final int timeout = getSocketTimeout();
		this.searchMx = searchMx;
		setSocketTimeout(timeout);
	}

	/**
	 * The current value for the the "ST" field of the search message.
	 */
	public String getSearchTarget() {
		return searchTarget;
	}

	/**
	 * What to include in the "ST" field of the search message.
	 * See <a href="http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf">UPnP Device Architecture 1.1</a>,
	 * page 31 for allowed values.
	 * Or use one of the predefined constants {@link #SEARCH_TARGET_ALL} and {@link #SEARCH_TARGET_ROOTDEVICE}.
	 * Defaults to {@link #SEARCH_TARGET_ALL}.
	 *
	 * @param searchTarget What to include in the "ST" field of the search message.
	 */
	public void setSearchTarget(String searchTarget) {
		this.searchTarget = searchTarget;
	}

	/**
	 * The time to live used for the multicast discovery message.
	 */
	public int getTimeToLive() {
		return ttl;
	}

	/**
	 * The time to live of the multicast discovery message. Defaults to 2.
	 *
	 * @param ttl The time to live of the multicast discovery message.
	 */
	public void setTimeToLive(int ttl) {
		this.ttl = ttl;
	}

	/**
	 * The time (in milliseconds) to wait for additional responses <em>after</em> {@link #searchMx} seconds have passed.
	 */
	public int getSocketTimeout() {
		return socketTimeout-(searchMx*1000);
	}

	/**
	 * Set the time (in milliseconds) to wait for additional responses <em>after</em> {@link #searchMx} seconds have passed.
	 * Defaults to 2000.
	 *
	 * @param socketTimeout the time (in milliseconds) to wait
	 */
	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = searchMx*1000+socketTimeout;
	}

	/**
	 * Send a SSDP search message and return a list of received responses.
	 */
	public List<? extends Response> discover() {
		return discover(searchTarget);
	}

	/**
	 * Send a SSDP search message with the given search target (ST) and return a list of received responses.
	 */
	public List<? extends Response> discover(String searchTarget) {
		final InetAddress address;
		// standard multicast port for SSDP
		try {
			// multicast address with administrative scope
			address = InetAddress.getByName(MULTICAST_ADDRESS);
		} catch(UnknownHostException e) {
			throw new IllegalStateException("Can not get multicast address", e);
		}

		final MulticastSocket socket;
		try {
			socket = new MulticastSocket(MULTICAST_PORT);
		} catch(IOException e) {
			throw new IllegalStateException("Can not create multicast socket", e);
		}

		try {
			socket.setSoTimeout(socketTimeout);
		} catch(SocketException e) {
			throw new IllegalStateException("Can not set socket timeout", e);
		}

		try {
			socket.joinGroup(address);
		} catch(IOException e) {
			throw new IllegalStateException("Can not make multicast socket joinGroup " +address.getHostAddress(), e);
		}

		try {
			socket.setTimeToLive(ttl);
		} catch(IOException e) {
			throw new IllegalStateException("Can not set TTL " + ttl, e);
		}

		final byte[] transmitBuffer;
		try {
			transmitBuffer = constructSearchMessage(searchTarget).getBytes(CHARSET_NAME);
		} catch(UnsupportedEncodingException e) {
			throw new IllegalStateException("WTF? " +CHARSET_NAME +" is not supported?", e);
		}
		final DatagramPacket packet = new DatagramPacket(transmitBuffer, transmitBuffer.length, address, MULTICAST_PORT);

		try {
			socket.send(packet);
			log("SSDP discover: Search request send.");
		} catch(IOException e) {
			throw new IllegalStateException("Can not send search request", e);
		}

		final ArrayList<Response> result = new ArrayList<>();
		try {
			byte[] receiveBuffer = new byte[8192];
			while(true) {
				final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				socket.receive(receivePacket);
				log(receivePacket);
				final Response response = parseResponsePacket(receivePacket);
				if(response!=null) {
					result.add(response);
				} else {
					log("Response from " +receivePacket.getAddress() +" appears to not have the proper formatting. Ignoring it.");
				}
			}
		} catch(SocketTimeoutException e) {
		} catch(IOException e) {
			throw new IllegalStateException("Problem receiving search responses", e);
		}

		return result;
	}

	String constructSearchMessage(String searchTarget) {
		return "M-SEARCH * HTTP/1.1\r\n"
			   + "HOST: " +MULTICAST_ADDRESS +":" +MULTICAST_PORT +"\r\n"
			   + "MAN: \"ssdp:discover\"\r\n"
			   + "MX: " + searchMx +"\r\n"
			   + "ST: " + searchTarget +"\r\n"
			   + "USER-AGENT: " +USER_AGENT_OS+"/"+USER_AGENT_OS_VERSION +" UPnP/1.1 "
			     + userAgentProduct +"/"+ userAgentProductVersion +"\r\n"
				;
	}

	Response parseResponsePacket(DatagramPacket responsePacket) {
		final Response response = new Response(responsePacket.getAddress());
		final String message;
		try {
			message = new String(responsePacket.getData(), 0, responsePacket.getLength(), CHARSET_NAME);
		} catch(UnsupportedEncodingException e) {
			throw new IllegalStateException("WTF? " +CHARSET_NAME +" is not supported?", e);
		}
		if(!message.startsWith(RESPONSE_MESSAGE_HEADER)) {
			return null;
		}

		final String[] lines = message.substring(RESPONSE_MESSAGE_HEADER.length()).split("[\\r\\n]+");
		String continuationHeader = null;
		for(String line : lines) {
			if(continuationHeader==null || !line.startsWith(" ")) {
				final Matcher matcher = HEADER_PATTERN.matcher(line);
				if(matcher.matches()) {
					final String header = matcher.group(1);
					final String value = matcher.group(2);
					if(response.headers.containsValue(header)) {
						// according to RFC 2616, Section 4.2 this may only be the case for lists
						// and in this case the values may be concatenated, separated by comma
						String existingValue = response.getHeader(header);
						if(existingValue==null) {
							existingValue = "";
						} else if(existingValue.trim().length()>0) {
							existingValue += ",";
						}
						response.setHeader(header, existingValue+value);
					} else {
						response.setHeader(header, value);
					}
					continuationHeader = header;
				} else {
					continuationHeader = null;
				}
			} else {
				String existingValue = response.getHeader(continuationHeader);
				if(existingValue==null) {
					existingValue = "";
				} else {
					existingValue += " ";
				}
				response.setHeader(continuationHeader, existingValue +line.trim());
			}
		}

		return response;
	}

	/**
	 * A single SSDP response.
	 * The response originated from {@link #address} and the headers are accessible by {@link #getHeaders()} or {@link #getHeader(String)}.
	 */
	public static class Response {
		final InetAddress address;
		final Map<String,String> headers = new LinkedHashMap<>();

		Response(InetAddress address) {
			this.address = address;
		}

		public InetAddress getAddress() {
			return address;
		}

		public Map<String, String> getHeaders() {
			return Collections.unmodifiableMap(headers);
		}

		public String getHeader(String fieldName) {
			return headers.get(fieldName.toUpperCase());
		}

		void setHeader(String fieldName, String fieldValue) {
			headers.put(fieldName.toUpperCase(), fieldValue);
		}

		@Override
		public String toString() {
			final StringBuilder result = new StringBuilder();
			result.append("Response from: ").append(address).append('\n');
			for(Map.Entry<String, String> entry : headers.entrySet()) {
				result.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
			return result.toString();
		}
	}

	private void log(String m) {
		if(debugLog!=null) {
			debugLog.println(m);
		}
	}

	private void log(DatagramPacket packet) {
		if(debugLog!=null) {
			debugLog.println("Response from: " + packet.getAddress());
			try {
				debugLog.println(new String(packet.getData(), 0, packet.getLength(), CHARSET_NAME));
			} catch(UnsupportedEncodingException e) {
				throw new IllegalStateException("WTF? " +CHARSET_NAME +" is not supported?", e);
			}
		}
	}
}
