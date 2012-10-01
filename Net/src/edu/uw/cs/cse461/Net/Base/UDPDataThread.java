package edu.uw.cs.cse461.Net.Base;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class UDPDataThread implements Runnable{
	private final static int TIMEOUT = 750;
	private int _portNumber;
	private DatagramSocket _dSocket;
	private boolean _timeToClose = false;
	private int _xferSize;
	
	public UDPDataThread(int portNumber, int xferSize){
		_portNumber = portNumber;
		_xferSize = xferSize;
		System.out.println("UDPDataThread constructor: server set up at port: " + portNumber);
	}
	
	
	@Override
	public void run() {
		try {
			_dSocket = new DatagramSocket(_portNumber);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		try {
			_dSocket.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("UDPDataThread.run: server started at port: " + _portNumber);
		
		boolean error = false;
		boolean timedOut = false;
		while(true){
			byte byteBuffer[] = new byte[_xferSize];
			DatagramPacket p = new DatagramPacket(byteBuffer,byteBuffer.length);
			try {
				_dSocket.receive(p);
			} catch (SocketTimeoutException e) {
				timedOut = true;
			} catch (IOException e) {
				System.err.println("UDPDataThread.run: IOException on accept.");
				error = true;
			} 
			
			if(!error && !timedOut){
				try {
					System.out.println("UDPDataThread.run: transmitting message of length " + _xferSize + ".");
					DatagramPacket message = new DatagramPacket(byteBuffer, byteBuffer.length);  //reuse the buffer since its the same length
					_dSocket.send(message);
					System.out.println("UDPDataThread.run: message transmitted.");
				} catch (IOException e1) {
					System.err.println("UDPDataThread.run: trouble sending response");
					e1.printStackTrace();
				}
				
			} 
			
			
			//check if time to close
			if(_timeToClose){
				_dSocket.close();
				return;				
			}
			
			error = false;
			timedOut = false;
		}
		
	}
	
	public void end(){
		_timeToClose = true;
	}
}

