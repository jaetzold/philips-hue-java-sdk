package com.jvmcode.networking;

import java.io.IOException;
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

/** @author Stephan Jaetzold <p><small>Created at 15.03.13, 16:34</small> */
public class SimpleServiceDiscovery {
	final int MULTICAST_PORT = 1900;
	final String MULTICAST_ADDRESS = "239.255.255.250";
	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1, Page 15, Paragraph 3
	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1.3.2, Page 30, Paragraph 4
	final int TTL = 2;	// this SHOULD be configurable
	final int SEARCH_MX = 2;	// maximum response wait time
	final int SO_TIMEOUT = SEARCH_MX*1000+2000;
	final String SEARCH_TARGET = "ssdp:all";
	final String USER_AGENT_OS = "Java";
	final String USER_AGENT_OS_VERSION = System.getProperty("java.version");
	final String USER_AGENT_PRODUCT = SimpleServiceDiscovery.class.getName();
	final String USER_AGENT_PRODUCT_VERSION = "0.1";

	final String SEARCH_MESSAGE =   "M-SEARCH * HTTP/1.1\r\n"
										 + "HOST: " +MULTICAST_ADDRESS +":" +MULTICAST_PORT +"\r\n"
										 + "MAN: \"ssdp:discover\"\r\n"
										 + "MX: " +SEARCH_MX +"\r\n"
										 + "ST: " +SEARCH_TARGET +"\r\n"
										 + "USER-AGENT: " +USER_AGENT_OS+"/"+USER_AGENT_OS_VERSION +" UPnP/1.1 "
										   +USER_AGENT_PRODUCT+"/"+ USER_AGENT_PRODUCT_VERSION+"\r\n"
			;
	final String RESPONSE_MESSAGE_HEADER = "HTTP/1.1 200 OK\r\n";

	static final String CHARSET_NAME = "UTF-8";
	static final Pattern HEADER_PATTERN = Pattern.compile("([^\\p{Cntrl} :]+):\\s*(.*)");

	public List<? extends Response> discover() {
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
			socket.setSoTimeout(SO_TIMEOUT);
		} catch(SocketException e) {
			throw new IllegalStateException("Can not set socket timeout", e);
		}

		try {
			socket.joinGroup(address);
		} catch(IOException e) {
			throw new IllegalStateException("Can not make multicast socket joinGroup " +address.getHostAddress(), e);
		}

		try {
			socket.setTimeToLive(TTL);
		} catch(IOException e) {
			throw new IllegalStateException("Can not set TTL " +TTL, e);
		}

		final byte[] transmitBuffer;
		try {
			transmitBuffer = SEARCH_MESSAGE.getBytes(CHARSET_NAME);
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
					log(response.toString());
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
						// according to RFC 2616, Section 4.2 this may only be the case for lists and in this case the values may be concatenated, separated by comma
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
			return headers.get(fieldName);
		}

		void setHeader(String fieldName, String fieldValue) {
			headers.put(fieldName, fieldValue);
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
		System.out.println(m);
	}

	private void log(DatagramPacket packet) {
		InetAddress addr = packet.getAddress();
		System.out.println("Response from: " + addr);
		try {
			System.out.println(new String(packet.getData(), 0, packet.getLength(), CHARSET_NAME));
		} catch(UnsupportedEncodingException e) {
			throw new IllegalStateException("WTF? " +CHARSET_NAME +" is not supported?", e);
		}
	}

	public static void main(String[] argv) {
		new SimpleServiceDiscovery().discover();
	}
}
