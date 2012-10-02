package edu.uw.cs.cse461.Net.Base;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/*************
 * Runnable server thread for a single socket.
 * Waits for a TCP connection to be established, then sends a packet of a given size.
 * Makes no guarantees what the packet's contents will be.
 * Call end() to signal the thread to shut down; this may take up to TIMEOUT ms to occur. 
 * @author mmattb
 *
 */
public class TCPDataThread implements DataThreadInterface {
	private final static int TIMEOUT = 750; //time in MS between checks to see if its time to shut down
	private int _portNumber;					//port number to receive connections in
	private ServerSocket _server;				//a server socket for the current available connection
	private boolean _timeToClose = false;		//flag set when end() is called to signal the thread to shut down
	private int _xferSize;						//size of server response in bytes
	
	/*************
	 * Constructs a new thread, but does not start it running
	 * @param portNumber - a valid network port number in this domain
	 * @param xferSize - the size in bytes of the response to be sent
	 */
	public TCPDataThread(int portNumber, int xferSize){
		_portNumber = portNumber;
		_xferSize = xferSize;
		System.out.println("TCPDataThread constructor: server set up at port: " + portNumber);
	}
	
	/*************
	 * Starts this thread running if called using the Java Thread functionality.
	 * Returns prematurely if the socket cannot be set up properly.  This can happen
	 *   for many reasons, including:
	 *   -_portNumber is not available
	 *   -an exception is thrown while the socket TIMEOUT is being set (perhaps a bad value)
	 * Once the socket is made, it will wait for a connection.  If a connection is established,
	 *   it will send a packet of size _xferSize bytes to the client.  There are no guarantees
	 *   what the contents of the packet will be.
	 * Every TIMEOUT ms, the thread will check the _timeToClose flag to see if its time to shut
	 *   this server thread down.  The flag can be set to true by a call to end() from another thread.
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
			_server.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("TCPDataThread.run: server started at port: " + _portNumber);
		
		//loop until time to close
		boolean error = false;
		boolean timedOut = false;
		while(true){
			Socket s = null;
			//set up socket to accept.  Code hangs on .accept() until a connection is established or
			// TIMEOUT ms have passed.
			try {
				s = _server.accept();
				System.out.println("TCPDataThread.run: TCP connection established.");
			} catch (SocketTimeoutException e) {
				timedOut = true;
			} catch (IOException e) {
				System.err.println("TCPDataThread.run: IOException on accept.");
				error = true;
			} 
			
			//if there was no connection error and the timeout did not expire, then we have made a connection
			//  and we attempt to send the response
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

