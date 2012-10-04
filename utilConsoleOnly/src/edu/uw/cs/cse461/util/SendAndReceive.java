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
	 * @return the number of bytes received through the connection, -1 if an error occurred before bytes were sent.
	 */
	public static int udpSendPacket(DatagramSocket socket, DatagramPacket packet, int len) {
		int received = 0;
		try {
			// Attempts to send the packet.
			try {
				socket.send(packet);
			} catch (IOException e) {
				// If the sending failed, return an error code
				return -1;
			}
		
			// The length of data expected is larger than can be sent in a single packet.
			// Thus an alternate array must be created to store the data until all of it arrives or
			// the socket times out.
			DatagramPacket response = new DatagramPacket(new byte[1000], 1000);
			while (received < len) {
				socket.receive(response);
				received += response.getLength();
			}
		} catch (SocketTimeoutException e) {
			e.printStackTrace();
			// If the socket times out, return what was received.
		} catch (IOException e) {
			e.printStackTrace();
			// If there is an error, return what was received, if anything.
		}
		return received;
	}

	/**
	 * 
	 * @param socket - the socket through which the message should be sent
	 * @param buf - a buffer containing the message to send.
	 * @return the response to the message from the server.
	 */
	public static String tcpSendMessage(Socket socket, byte[] buf) {
		try {
			InputStream result = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			os.write(buf);
			
			int len = 0;
			while ( len < buf.length) {
				len += result.read(buf, len, buf.length);
			}
			String response = new String(buf, 0, buf.length);
			return response;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
