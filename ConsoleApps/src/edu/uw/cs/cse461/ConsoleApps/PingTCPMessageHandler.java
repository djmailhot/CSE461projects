package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

import edu.uw.cs.cse461.ConsoleApps.PingInterface.PingTCPMessageHandlerInterface;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.Net.TCPMessageHandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;
import edu.uw.cs.cse461.util.Log;

public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingTCPMessageHandlerInterface {
	private static final String TAG="PingTCPMessageHandler";
	private static final int MAX_RESPONSE_LENGTH = 1000;
	
	public PingTCPMessageHandler() {
		super("pingtcpmessagehandler", true);
	}


	@Override
	public ElapsedTimeInterval ping(String hostIP, int port, int socketTimeout,
			int nTrials) throws Exception {
		ElapsedTime.start("PingTCPMessage");
		for(int i = 0; i<nTrials; i++){
			// Attempts to create a TCP socket connecting to the given address
			Socket socket;
			try {
				socket = new Socket(hostIP, port);
			} catch (IOException e) {
				e.printStackTrace();
				Log.w(TAG,"Failed to establish connection");
				break;
			}
				
			
			// Attempts to set the socket's timeout to the provided value.
			try {
				socket.setSoTimeout(socketTimeout);
			} catch (SocketException e) {
				e.printStackTrace();
				try {
					socket.close();
				} catch (IOException er) {
					er.printStackTrace();
				}
				Log.w(TAG,"Failed to set socket timeout");
				break;
			}
			
			
			// Create message handler wrapper around the socket
			//  Assumes socket is connected and the timeout is set
			TCPMessageHandler tmh;
			try {
				tmh = new TCPMessageHandler(socket);
			} catch (IOException e) {
				e.printStackTrace();
				Log.w(TAG,"Failed to set up TCPMessageHandler");
				break;
			}
			
			
			tmh.setMaxReadLength(MAX_RESPONSE_LENGTH);
			
			
			// Attempts to send and receive a 0-length ping from the server.
			try {
				tmh.sendMessage("");
				String response = tmh.readMessageAsString();
			} catch (IOException e) {
				e.printStackTrace();
				
				//attempt to clean up on failure
				try {
					tmh.discard();
					socket.close();
				} catch (IOException er) {
					er.printStackTrace();
				}
				Log.w(TAG,"Failed to receive response");
				break;
			}    							
			
			// Attempts to close the socket
			try {
				tmh.discard();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
				Log.w(TAG,"Failed to close the socket");
				break;
			}
		}
		ElapsedTime.stop("PingTCPMessage");
		return ElapsedTime.get("PingTCPMessage");
	}

	@Override
	public void run() throws Exception {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				ElapsedTime.clear();

				String targetIP = config.getProperty("pingtcpmessagehandler.server");
				if ( targetIP == null ) {
					System.out.println("No echoraw.server entry in config file.");
					System.out.print("Enter a host ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				
				int targetTCPPort = config.getAsInt("pingtcpmessagehandler.port", 0, TAG);
				if ( targetTCPPort == 0 ) {
					System.out.print("Enter the server's TCP port, or empty line to skip: ");
					String targetTCPPortStr = console.readLine();
					if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
					else targetTCPPort = Integer.parseInt(targetTCPPortStr);
				}

				int nTrials = config.getAsInt("ping.ntrials", 5, TAG);
				int socketTimeout = config.getAsInt("ping.sockettimeout", 500, TAG);
				
				System.out.println("Host: " + targetIP);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval tcpResult = null;

				if ( targetTCPPort != 0 ) {
					tcpResult = ping(targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec", tcpResult.mean()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingTCPMessageHandler.run() caught exception: " +e.getMessage());
		}
	}
	
}
