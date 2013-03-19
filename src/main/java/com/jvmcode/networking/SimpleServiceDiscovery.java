package com.jvmcode.networking;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** @author Stephan Jaetzold <p><small>Created at 15.03.13, 16:34</small> */
public class SimpleServiceDiscovery {
	static final int MULTICAST_PORT = 1900;
	static final String MULTICAST_ADDRESS = "239.255.255.250";
	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1, Page 15, Paragraph 3
	// http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf, Section 1.3.2, Page 30, Paragraph 4
	static final int TTL = 2;	// this SHOULD be configurable
	static final int SEARCH_MX = 2;	// maximum response wait time
	static final int SO_TIMEOUT = SEARCH_MX*1000+5000;
	static final String SEARCH_TARGET = "ssdp:all";
	static final String USER_AGENT_OS = "Java";
	static final String USER_AGENT_OS_VERSION = System.getProperty("java.version");
	static final String USER_AGENT_PRODUCT = SimpleServiceDiscovery.class.getName();
	static final String USER_AGENT_PRODUCT_VERSION = "0.1";

	static final String SEARCH_MESSAGE =   "M-SEARCH * HTTP/1.1\r\n"
										 + "HOST: " +MULTICAST_ADDRESS +":" +MULTICAST_PORT +"\r\n"
										 + "MAN: \"ssdp:discover\"\r\n"
										 + "MX: " +SEARCH_MX +"\r\n"
										 + "ST: " +SEARCH_TARGET +"\r\n"
										 + "USER-AGENT: " +USER_AGENT_OS+"/"+USER_AGENT_OS_VERSION +" UPnP/1.1 "
										   +USER_AGENT_PRODUCT+"/"+ USER_AGENT_PRODUCT_VERSION+"\r\n"
			;
	static final String CHARSET_NAME = "UTF-8";

	public static Object discover() {
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

		try {
			byte[] receiveBuffer = new byte[8192];
			while(true) {
				final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				socket.receive(receivePacket);
				log(receivePacket);
			}
		} catch(SocketTimeoutException e) {
		} catch(IOException e) {
			throw new IllegalStateException("Problem receiving search responses", e);
		}

		return null;
	}

	private static void log(String m) {
		System.out.println(m);
	}

	private static void log(DatagramPacket packet) {
		InetAddress addr = packet.getAddress();
		System.out.println("Response from: " + addr);
		try {
			System.out.println(new String(packet.getData(), 0, packet.getLength(), CHARSET_NAME));
		} catch(UnsupportedEncodingException e) {
			throw new IllegalStateException("WTF? " +CHARSET_NAME +" is not supported?", e);
		}
	}

	public static void main(String[] argv) {
		discover();
	}
}
