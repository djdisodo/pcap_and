package com.gitlab.djdisodo.ksp.packetcapturedemo;

import org.savarese.vserv.tcpip.TCPPacket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class RawTcpPacketHandler {

	private final HashMap<InetSocketAddress, TcpPacketForwarder> forwarders = new HashMap<>();

	private OutputStream rawSocketOutputStream;

	public RawTcpPacketHandler(OutputStream rawSocketOutputStream) {
		this.rawSocketOutputStream = rawSocketOutputStream;
	}

	public void handle(final TCPPacket packet) throws IOException {
		final InetSocketAddress socketAddress = new InetSocketAddress(
				packet.getDestinationAsInetAddress(),
				packet.getDestinationPort()
		);
		TcpPacketForwarder tcpPacketForwarder = forwarders.get(socketAddress);
		if (tcpPacketForwarder == null) {
			tcpPacketForwarder = new TcpPacketForwarder(socketAddress, this.rawSocketOutputStream);
			forwarders.put(socketAddress, tcpPacketForwarder);
		}
		tcpPacketForwarder.forward(packet);
	}
}
