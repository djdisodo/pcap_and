package com.gitlab.djdisodo.ksp.packetcapturedemo;

import org.savarese.vserv.tcpip.TCPPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.sql.Time;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class RawTcpServer extends Socket {

	private TcpSocketStatus status = TcpSocketStatus.CLOSED;

	private OutputStream rawSocketOutputStream;

	public static final String LOCAL_IP = "10.5.37.1";

	private int sequenceNumber = 0;

	private long ackNumber;

	private PipedOutputStream readOutputStream;

	private PipedInputStream readInputStream;

	private PipedOutputStream writeOutputStream;

	private PipedInputStream writeInputStream;

	private Thread forwardThread;

	private int destination;
	private int source;

	private int destinationPort;
	private int sourcePort;

	private boolean waitingAck = false;

	public RawTcpServer(OutputStream rawSocketOutputStream) throws IOException {
		this.rawSocketOutputStream = rawSocketOutputStream;
	}

	public void input(final TCPPacket packet) throws IOException {
		if (packet.isSet(TCPPacket.MASK_ACK) && !packet.isSet(TCPPacket.MASK_RST)) {
			if (packet.getAckNumber() != sequenceNumber | packet.getSequenceNumber() != ackNumber) {
				//todo send rst
				return;
			}
			if (waitingAck) {
				if (status == TcpSocketStatus.LAST_ACK) {
					status = TcpSocketStatus.CLOSED;
					System.out.println("closed");
				}
				waitingAck = false;
			}
			if (status == TcpSocketStatus.CLOSE_WAIT) {
				final TCPPacket tcpPacket = new TCPPacket(new byte[40]);
				tcpPacket.setIPVersion(4);
				tcpPacket.setProtocol(6);
				tcpPacket.setIdentification(new Random().nextInt(65565));
				tcpPacket.setIPHeaderLength(20 >> 2);
				tcpPacket.setTCPHeaderLength(20 >> 2);
				tcpPacket.setIPPacketLength(40);
				tcpPacket.setWindowSize(packet.getWindowSize());
				tcpPacket.setTTL(1);
				tcpPacket.setUrgentPointer(0);
				tcpPacket.setControlFlags(TCPPacket.MASK_FIN);
				tcpPacket.setSequenceNumber(sequenceNumber);
				tcpPacket.setAckNumber(ackNumber);
				tcpPacket.setSourceAsWord(packet.getDestinationAsWord());
				tcpPacket.setSourcePort(packet.getDestinationPort());
				tcpPacket.setDestinationAsWord(packet.getSourceAsWord());
				tcpPacket.setDestinationPort(packet.getSourcePort());
				tcpPacket.computeTCPChecksum(true);
				tcpPacket.computeIPChecksum(true);
				rawSocketOutputStream.write(tcpPacket.getData());
				rawSocketOutputStream.flush();
				status = TcpSocketStatus.LAST_ACK;
			}
		}
		switch (status) {
			case LISTEN:
				if (packet.isSetOnly(TCPPacket.MASK_SYN)) {
					status = TcpSocketStatus.SYN_RECV;
					System.out.println("syn_rcv");
					ackNumber = packet.getSequenceNumber();
					ackNumber++;
					final TCPPacket tcpPacket = new TCPPacket(new byte[40]);
					tcpPacket.setIPVersion(4);
					tcpPacket.setProtocol(6);
					tcpPacket.setIdentification(new Random().nextInt(65565));
					tcpPacket.setIPHeaderLength(20 >> 2);
					tcpPacket.setTCPHeaderLength(20 >> 2);
					tcpPacket.setIPPacketLength(40);
					tcpPacket.setWindowSize(packet.getWindowSize());
					tcpPacket.setTTL(1);
					tcpPacket.setUrgentPointer(0);
					tcpPacket.setControlFlags(TCPPacket.MASK_SYN | TCPPacket.MASK_ACK);
					tcpPacket.setSequenceNumber(sequenceNumber);
					tcpPacket.setAckNumber(ackNumber);
					tcpPacket.setSourceAsWord(packet.getDestinationAsWord());
					tcpPacket.setSourcePort(packet.getDestinationPort());
					tcpPacket.setDestinationAsWord(packet.getSourceAsWord());
					tcpPacket.setDestinationPort(packet.getSourcePort());
					tcpPacket.computeTCPChecksum(true);
					tcpPacket.computeIPChecksum(true);
					rawSocketOutputStream.write(tcpPacket.getData(), 0, 40);
					rawSocketOutputStream.flush();
					readOutputStream = new PipedOutputStream();
					readInputStream = new PipedInputStream(readOutputStream);
					writeOutputStream = new PipedOutputStream();
					writeInputStream = new PipedInputStream(writeOutputStream);
					sequenceNumber++;
				}
				break;
			case SYN_RECV:
				if (packet.isSet(TCPPacket.MASK_ACK)) {
					status = TcpSocketStatus.ESTABLISHED;

					destination = packet.getSourceAsWord();
					source = packet.getDestinationAsWord();

					destinationPort = packet.getDestinationPort();
					sourcePort = packet.getSourcePort();

					forwardThread = new TransferThread();
					forwardThread.start();
					System.out.println("established");
				} else {
					return;
				}
			case ESTABLISHED:
				if (packet.isSet(TCPPacket.MASK_PSH)) {
					ackNumber += packet.getTCPDataByteLength();
					this.readOutputStream.write(
							packet.getData(),
							packet.getCombinedHeaderByteLength(),
							packet.getTCPDataByteLength()
					);
					final TCPPacket tcpPacket = new TCPPacket(new byte[40]);
					tcpPacket.setIPVersion(4);
					tcpPacket.setProtocol(6);
					tcpPacket.setIdentification(new Random().nextInt(65565));
					tcpPacket.setIPHeaderLength(20 >> 2);
					tcpPacket.setTCPHeaderLength(20 >> 2);
					tcpPacket.setIPPacketLength(40);
					tcpPacket.setWindowSize(packet.getWindowSize());
					tcpPacket.setTTL(1);
					tcpPacket.setUrgentPointer(0);
					tcpPacket.setControlFlags(TCPPacket.MASK_ACK);
					tcpPacket.setSequenceNumber(sequenceNumber);
					tcpPacket.setAckNumber(ackNumber);
					tcpPacket.setSourceAsWord(packet.getDestinationAsWord());
					tcpPacket.setSourcePort(packet.getDestinationPort());
					tcpPacket.setDestinationAsWord(packet.getSourceAsWord());
					tcpPacket.setDestinationPort(packet.getSourcePort());
					tcpPacket.computeTCPChecksum(true);
					tcpPacket.computeIPChecksum(true);
					rawSocketOutputStream.write(tcpPacket.getData(), 0, 40);
					rawSocketOutputStream.flush();
				}
				if (packet.isSet(TCPPacket.MASK_FIN)) {
					System.out.println("fin");
					final TCPPacket tcpPacket = new TCPPacket(new byte[40]);
					tcpPacket.setIPVersion(4);
					tcpPacket.setProtocol(6);
					tcpPacket.setIdentification(new Random().nextInt(65565));
					tcpPacket.setIPHeaderLength(20 >> 2);
					tcpPacket.setTCPHeaderLength(20 >> 2);
					tcpPacket.setIPPacketLength(40);
					tcpPacket.setWindowSize(packet.getWindowSize());
					tcpPacket.setTTL(1);
					tcpPacket.setUrgentPointer(0);
					tcpPacket.setControlFlags(TCPPacket.MASK_ACK);
					tcpPacket.setSequenceNumber(sequenceNumber);
					tcpPacket.setAckNumber(ackNumber);
					tcpPacket.setSourceAsWord(packet.getDestinationAsWord());
					tcpPacket.setSourcePort(packet.getDestinationPort());
					tcpPacket.setDestinationAsWord(packet.getSourceAsWord());
					tcpPacket.setDestinationPort(packet.getSourcePort());
					tcpPacket.computeTCPChecksum(true);
					tcpPacket.computeIPChecksum(true);
					rawSocketOutputStream.write(tcpPacket.getData(), 0, 40);
					rawSocketOutputStream.flush();
					status = TcpSocketStatus.CLOSE_WAIT;
					writeOutputStream.write(new byte[1500]);
					writeOutputStream.flush();
					readInputStream.close();
					readOutputStream.close();
					writeInputStream.close();
					writeOutputStream.close();
				}
				break;
			case CLOSE_WAIT:
				break;
		}
	}

	public TcpSocketStatus getStatus() {
		return status;
	}

	public void accept() {
		status = TcpSocketStatus.LISTEN;
	}

	public InputStream getInputStream() throws IOException {
		if (readInputStream == null) {
			throw new IOException("socket not connected");
		}
		return readInputStream;
	}


	@Override
	public OutputStream getOutputStream() throws IOException {
		if (writeOutputStream == null) {
			throw new IOException("socket not connected");
		}
		return writeOutputStream;
	}

	public boolean isClosed() {
		return status == TcpSocketStatus.CLOSED;
	}


	public boolean isConnected() {
		return status == TcpSocketStatus.ESTABLISHED;
	}

	private class TransferThread extends Thread {
		@Override
		public void run() {
			int read = 0;
			while (this.isInterrupted()) {
				byte[] buffer = BufferPool.get();
				final TCPPacket tcpPacket = new TCPPacket(buffer);
				try {
					read = writeInputStream.read(buffer, 40, 1460);
					System.out.println("read");

				} catch (IOException e) {
					e.printStackTrace();
					break;
				}

			}
		}
	}


	private class RawTcpServerOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {

		}

		@Override
		public void write(byte[] b) throws IOException {
			super.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			TCPPacket tcpPacket = new TCPPacket(new byte[len + 40]);
			tcpPacket.setIPVersion(4);
			tcpPacket.setProtocol(6);
			tcpPacket.setIdentification(new Random().nextInt(65565));
			tcpPacket.setIPHeaderLength(20 >> 2);
			tcpPacket.setTCPHeaderLength(20 >> 2);
			tcpPacket.setIPPacketLength(40);
			tcpPacket.setTCPDataByteLength(len);
			tcpPacket.setTTL(1);
			tcpPacket.setUrgentPointer(0);
			tcpPacket.setControlFlags(TCPPacket.MASK_FIN);
			tcpPacket.setSequenceNumber(sequenceNumber);
			tcpPacket.setAckNumber(ackNumber);
			tcpPacket.setSourceAsWord(source);
			tcpPacket.setSourcePort(sourcePort);
			tcpPacket.setDestinationAsWord(destination);
			tcpPacket.setDestinationPort(destinationPort);
			tcpPacket.computeTCPChecksum(true);
			tcpPacket.computeIPChecksum(true);

			try {
				rawSocketOutputStream.write(tcpPacket.getData(), 0, 40);
				rawSocketOutputStream.flush();
				waitingAck = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
			/*new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					if (waitingAck) {
						try {
							rawSocketOutputStream.write(tcpPacket.getData(), 0, 40);
							rawSocketOutputStream.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
						new Timer().schedule(this, 3000);
					}
				}
			}, 3000);*/
		}
	}
}
