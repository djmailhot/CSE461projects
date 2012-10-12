package edu.uw.cs.cse461.Net.Base;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.json.JSONObject;

import edu.uw.cs.cse461.Net.TCPMessageHandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.Log;

public class TCPMessageHandlerThread implements DataThreadInterface {
	private static final String TAG = "TCPMessageHandlerThread";
	private static final String TRANSFER_SIZE_KEY = "transferSize";
	
	private int _timeOut = 750; //time in MS between checks to see if its time to shut down
	private int _portNumber;					//port number to receive connections in
	private ServerSocket _server;				//a server socket for the current available connection
	private TCPMessageHandler handler;			//a TCPMessageHandler to handle the connection details
	private boolean _timeToClose = false;		//flag set when end() is called to signal the thread to shut down
	private int _maxPacketSize;						//size of server response in bytes
	
	/*************
	 * Constructs a new thread, but does not start it running
	 * @param portNumber - a valid network port number in this domain
	 * @param maxPacketSize - the max size in bytes of each response packet
	 */
	public TCPMessageHandlerThread(int portNumber, int maxPacketSize, int timeout){
		_portNumber = portNumber;
		_maxPacketSize = maxPacketSize;
		_timeOut = timeout;
		handler = null;
		Log.i(TAG, "server set up at port: " + portNumber);
	}
	
	/*************
	 * Starts this running in its own thread if called using the Java Thread functionality.
	 * Returns prematurely if the socket cannot be set up properly.  This can happen
	 *   for many reasons, including:
	 *     - _portNumber is not available
	 *     - an exception is thrown while the socket TIMEOUT is being set (perhaps a bad value)
	 * Once the socket is made, it will wait for a connection.  If a connection is established,
	 *   it will send packets of size _maxPacketSize bytes to the client.  There are no guarantees
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
			
		//loop until time to close
		boolean error = false;
		boolean timedOut = false;
		Socket s = null;
		while(true){
			
			Log.i(TAG, "server started at port: " + _portNumber);
			//set up socket to accept.  Code hangs on .accept() until a connection is established or
			// TIMEOUT ms have passed.
			try {
				s = _server.accept();
				handler = new TCPMessageHandler(s);
				Log.i(TAG, "TCP connection established.");
			} catch (SocketTimeoutException e) {
				timedOut = true;
			} catch (IOException e) {
				Log.w(TAG, "IOException on accept.");
				error = true;
			} 
			
			//if there was no connection error and the timeout did not expire, then we have made a connection
			//  and we attempt to send the response
			if(!error && !timedOut){
				try {
					JSONObject jsonRequest = handler.readMessageAsJSONObject();
					int xferSize = jsonRequest.getInt(TRANSFER_SIZE_KEY);

					Log.i(TAG, "transmitting message of length " + xferSize + " to " + s.getInetAddress());

					int bitsLeft = xferSize;
					while (bitsLeft > 0) {
						int packetSize = (bitsLeft < _maxPacketSize) ? bitsLeft : _maxPacketSize;
						byte message[] = new byte[packetSize];

						handler.sendMessage(message);
						bitsLeft -= packetSize;

						Log.d(TAG, packetSize+"-bit packet sent.");
					}
				} catch (Exception e1) {
					Log.w(TAG, "trouble sending response");
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
					
					
					Log.i(TAG, "closing connection.");
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
