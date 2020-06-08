package com.gitlab.djdisodo.ksp.packetcapturedemo;

import org.savarese.vserv.tcpip.IPPacket;
import org.savarese.vserv.tcpip.TCPPacket;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class SocketReaderThread extends Thread{

	private InputStream inputStream;
	private OutputStream outputStream;

	public RawTcpPacketHandler rawTcpPacketHandler;

	public SocketReaderThread(InputStream inputStream, OutputStream outputStream) {
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.rawTcpPacketHandler = new RawTcpPacketHandler(outputStream);
	}

	@Override
	public void run() {
		byte[] buffer = new byte[1500];
		try {
			while (!interrupted()) {
				Arrays.fill(buffer, (byte) 0x00);
				inputStream.read(buffer);
				IPPacket ipPacket = new IPPacket(buffer);
				if (ipPacket.getProtocol() == 6) {
					//tcp
					this.rawTcpPacketHandler.handle(new TCPPacket(buffer));
				}
				//outputStream.write(buffer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
