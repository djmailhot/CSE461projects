package edu.uw.cs.cse461.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SendAndReceive {
	/**
	 * 
	 * @param socket - the socket to send the packet from and receive a response from
	 * @param packet - the packet to send through the socket to the server
	 * @param len - the length of the total amount of data should be received
	 * @return the number of bytes received through the connection, -1 if an error occurred before bytes were received.
	 */
	public static int udpSendPacket(DatagramSocket socket, DatagramPacket packet, int len) {
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
		// Attempts to send the packet.
		
		if (len >= 1000) {
			// The length of data expected is larger than can be sent in a single packet.
			// Thus an alternate array must be created to store the data until all of it arrives or
			// the socket times out.
			DatagramPacket response = new DatagramPacket(new byte[1000], 1000);
			int received = 0;
			try {
				while (received < len) {
					socket.receive(response);
					
					received += response.getLength();
				}
			} catch (SocketTimeoutException e) {
				return received;
				// If the socket times out, return what was received.
			} catch (IOException e) {
				e.printStackTrace();
				return -1;
			}
			return received;
		} else {
			DatagramPacket response = new DatagramPacket(new byte[len], len);
			try {
				socket.receive(response);
			} catch (IOException e) {
				e.printStackTrace();
				return -1;
			}
			return response.getLength();
		}
	}

	public static String tcpSendMessage(Socket socket, byte[] buf) {
		try {
			InputStream result = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			os.write(buf);
			try {
				int len = 0;
				while ( len < buf.length) {
					len += result.read(buf, len, buf.length);
				}
				String response = new String(buf, 0, buf.length);
				return response;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
