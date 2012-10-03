package edu.uw.cs.cse461.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SendAndReceive {
	public static byte[] udpSendPacket(DatagramSocket socket, DatagramPacket packet, int len) {
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		if (len >= 1000) {
			byte[] result = new byte[len];
			DatagramPacket response = new DatagramPacket(new byte[1000], 1000);
			int received = 0;
			try {
				while (received < len) {
					socket.receive(response);
					received += response.getLength();
				}
			} catch (SocketTimeoutException e) {
				return result;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			return result;
		} else {
			DatagramPacket response = new DatagramPacket(new byte[len], len);
			try {
				socket.receive(response);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			return response.getData();
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
