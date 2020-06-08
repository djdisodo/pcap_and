package com.gitlab.djdisodo.ksp.packetcapturedemo;

import org.savarese.vserv.tcpip.TCPPacket;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TcpPacketForwarder implements Closeable {

	public RawTcpServer rawTcpServer;

	public Socket tcpSocket;

	public InetSocketAddress remoteAddress;

	public byte[] buffer = new byte[1500];

	public TcpPacketForwarder(InetSocketAddress inetSocketAddress, OutputStream rawSocketOutputStream) throws IOException{
		this.rawTcpServer = new RawTcpServer(rawSocketOutputStream);
		rawTcpServer.accept();
		this.remoteAddress = inetSocketAddress;
	}


	public void forward(final TCPPacket packet) throws IOException{
		if (packet.isSetOnly(TCPPacket.MASK_SYN) && rawTcpServer.getStatus() == TcpSocketStatus.LISTEN) {
			tcpSocket = new Socket();
			tcpSocket.connect(new InetSocketAddress(packet.getDestinationAsInetAddress(), packet.getDestinationPort()));
			if (tcpSocket.isConnected()) {
				rawTcpServer.input(packet);
			}
		} else {
			rawTcpServer.input(packet);
		}
		if (rawTcpServer.isConnected()) {
			while (rawTcpServer.getInputStream().available() > 0) {
				int read = rawTcpServer.getInputStream().read(buffer);
				tcpSocket.getOutputStream().write(buffer, 0, read);
				tcpSocket.getOutputStream().flush();
			}
		}
		if (rawTcpServer.isClosed()) {
			tcpSocket.shutdownInput();
			tcpSocket.shutdownOutput();
		}

		System.out.println("forwarded");
	}

	public boolean isOpen() {
		return true;
	}

	@Override
	public void close() throws IOException {

	}
}
