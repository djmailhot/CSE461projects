package edu.uw.cs.cse461.Net.Base;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class TCPDataThread implements Runnable{
	private final static int TIMEOUT = 750;
	private int _portNumber;
	private ServerSocket _server;
	private boolean _timeToClose = false;
	private int _xferSize;
	
	public TCPDataThread(int portNumber, int xferSize){
		_portNumber = portNumber;
		_xferSize = xferSize;
		System.out.println("TCPDataThread constructor: server set up at port: " + portNumber);
	}
	
	
	@Override
	public void run() {
		try {
			_server = new ServerSocket(_portNumber);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		try {
			_server.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("TCPDataThread.run: server started at port: " + _portNumber);
		
		boolean error = false;
		boolean timedOut = false;
		while(true){
			Socket s = null;
			try {
				s = _server.accept();
				System.out.println("TCPDataThread.run: TCP connection established.");
			} catch (SocketTimeoutException e) {
				timedOut = true;
			} catch (IOException e) {
				System.err.println("TCPDataThread.run: IOException on accept.");
				error = true;
			} 
			
			if(!error && !timedOut){
				try {
					System.out.println("TCPDataThread.run: transmitting message of length " + _xferSize + ".");
					OutputStream os = s.getOutputStream();
					byte message[] = new byte[_xferSize];
					os.write(message);
					System.out.println("TCPDataThread.run: message transmitted.");
				} catch (IOException e1) {
					System.err.println("TCPDataThread.run: trouble sending response");
					e1.printStackTrace();
				}
				
				try {
					s.close();
					System.out.println("TCPDataThread.run: closing connection.");
				} catch (IOException e) {e.printStackTrace();}
			} 
			
			
			//check if time to close
			if(_timeToClose){
				try {
					_server.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
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

