package edu.uw.cs.cse461.Net.Base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import edu.uw.cs.cse461.Net.TCPMessageHandler.TCPMessageHandler;

public class TCPMessageHandlerThread implements DataThreadInterface {
	private int _timeOut = 750; //time in MS between checks to see if its time to shut down
	private int _portNumber;					//port number to receive connections in
	private ServerSocket _server;				//a server socket for the current available connection
	private TCPMessageHandler handler;			//a TCPMessageHandler to handle the connection details
	private boolean _timeToClose = false;		//flag set when end() is called to signal the thread to shut down
	private int _xferSize;						//size of server response in bytes
	
	/*************
	 * Constructs a new thread, but does not start it running
	 * @param portNumber - a valid network port number in this domain
	 * @param xferSize - the size in bytes of the response to be sent
	 */
	public TCPMessageHandlerThread(int portNumber, int xferSize, int timeout){
		_portNumber = portNumber;
		_xferSize = xferSize;
		_timeOut = timeout;
		handler = null;
		System.out.println("TCPMessageHandlerThread constructor: server set up at port: " + portNumber);
	}
	
	/*************
	 * Starts this running in its own thread if called using the Java Thread functionality.
	 * Returns prematurely if the socket cannot be set up properly.  This can happen
	 *   for many reasons, including:
	 *     - _portNumber is not available
	 *     - an exception is thrown while the socket TIMEOUT is being set (perhaps a bad value)
	 * Once the socket is made, it will wait for a connection.  If a connection is established,
	 *   it will send a packet of size _xferSize bytes to the client.  There are no guarantees
	 *   what the contents of the packet will be.
	 * Every TIMEOUT ms, the thread will check the _timeToClose flag to see if its time to shut
	 *   this server thread down.  The flag can be set to 'true' by a call to end() from this or 
	 *   another thread.
	 */
	@Override
	public void run() {
		//attempt to set up socket on target port number
		try {
			_server = new ServerSocket(_portNumber);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		//attempt to set the timeout between successive checks of _timeToClose flag
		try {
			_server.setSoTimeout(_timeOut);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("TCPMessageHandlerThread.run: server started at port: " + _portNumber);
			
		//loop until time to close
		boolean error = false;
		boolean timedOut = false;
		Socket s = null;
		while(true){
			//set up socket to accept.  Code hangs on .accept() until a connection is established or
			// TIMEOUT ms have passed.
			try {
				s = _server.accept();
				handler = new TCPMessageHandler(s);
				System.out.println("TCPMessageHandlerThread.run: TCP connection established.");
			} catch (SocketTimeoutException e) {
				timedOut = true;
			} catch (IOException e) {
				System.err.println("TCPMessageHandlerThread.run: IOException on accept.");
				error = true;
			} 
			
			//if there was no connection error and the timeout did not expire, then we have made a connection
			//  and we attempt to send the response
			if(!error && !timedOut){
				try {
					handler.readMessageAsBytes();
					System.out.println("TCPMessageHandlerThread.run: transmitting message of length " + _xferSize + " to " + s.getInetAddress());
					byte message[] = new byte[_xferSize];
					handler.sendMessage(message);
					System.out.println("TCPMessageHandlerThread.run: message transmitted.");
				} catch (Exception e1) {
					System.err.println("TCPMessageHandlerThread.run: trouble sending response");
					e1.printStackTrace();
				}
				
				try {
					s.close();
					handler.discard();
					handler = null;
					try {
						_server.close();
						_server = new ServerSocket(_portNumber);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
					
					//attempt to set the timeout between successive checks of _timeToClose flag
					try {
						_server.setSoTimeout(_timeOut);
					} catch (SocketException e) {
						e.printStackTrace();
						return;
					}
					
					
					System.out.println("TCPMessageHandlerThread.run: closing connection.");
					System.out.println("TCPMessageHandlerThread.run: server started at port: " + _portNumber);
				} catch (IOException e) {e.printStackTrace();}
			} 
			
			
			//check if time to close; close if it is
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
	
	/*************
	 * Call this function from the parent process to end this server thread.
	 * May take up to TIMEOUT ms until the thread checks this value and shuts down.
	 */
	public void end(){
		_timeToClose = true;
	}
}
