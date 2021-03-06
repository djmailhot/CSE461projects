package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import edu.uw.cs.cse461.ConsoleApps.DataXferInterface.DataXferRawInterface;
import edu.uw.cs.cse461.Net.Base.DataXferRawService;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.util.SendAndReceive;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

/**
 * Raw sockets version of ping client.
 * @author zahorjan
 *
 */
public class DataXferRaw extends NetLoadableConsoleApp implements DataXferRawInterface {
	private static final String TAG="DataXferRaw";

	// ConsoleApp's must have a constructor taking no arguments
	public DataXferRaw() throws Exception {
		super("dataxferraw", true);
	}

	/**
	 * This method is invoked each time the infrastructure is asked to launch this application.
	 */
	@Override
	public void run() {
		
		try {

			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("dataxferraw.server");
			if ( server == null ) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if ( server == null ) return;
				if ( server.equals("exit")) return;
			}

			int basePort = config.getAsInt("dataxferraw.baseport", -1, TAG);
			if ( basePort == -1 ) {
				System.out.print("Enter port number, or empty line to exit: ");
				String portStr = console.readLine();
				if ( portStr == null || portStr.trim().isEmpty() ) return;
				basePort = Integer.parseInt(portStr);
			}
			
			int socketTimeout = config.getAsInt("dataxferraw.sockettimeout", -1, TAG);
			if ( socketTimeout < 0 ) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);
				
			}

			int nTrials = config.getAsInt("dataxferraw.ntrials", -1, TAG);
			if ( nTrials == -1 ) {
				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				nTrials = Integer.parseInt(trialStr);
			}

			for ( int index=0; index<DataXferRawService.NPORTS; index++ ) {

				TransferRate.clear();
				
				int port = basePort + index;
				int xferLength = DataXferRawService.XFERSIZE[index];

				System.out.println("\n" + xferLength + " bytes");

				//-----------------------------------------------------
				// UDP transfer
				//-----------------------------------------------------

				TransferRateInterval udpStats = udpDataXfer(server, port, socketTimeout, xferLength, nTrials);
				
				System.out.println("UDP: xfer rate = " + String.format("%9.0f", udpStats.mean() * 1000.0) + " bytes/sec.");
				System.out.println("UDP: failure rate = " + String.format("%5.1f", udpStats.failureRate()) +
						           " [" + udpStats.nAborted() + "/" + udpStats.nTrials() + "]");

				//-----------------------------------------------------
				// TCP transfer
				//-----------------------------------------------------

				TransferRateInterval tcpStats = tcpDataXfer(server, port, socketTimeout, xferLength, nTrials);

				System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
				System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
						           " [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");

			}
			
		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}
	
	/**
	 * Performs nTrials trials via UDP of a data xfer to host hostIP on port udpPort.  Expects to get xferLength
	 * bytes in total from that host/port.  Is willing to wait up to socketTimeout msec. for new data to arrive.
	 * @return A TransferRateInterval object that measured the total bytes of data received over all trials and
	 * the total time taken.  The measured time should include socket creation time.
	 */
	@Override
	public TransferRateInterval udpDataXfer(String hostIP, int udpPort, int socketTimeout, int xferLength, int nTrials) {
		for (int i = 0; i < nTrials; i++) {
			TransferRate.start("udp");
			int totalTransferred = 0;

			DatagramSocket socket;	
			try {
				// Attempts to create a socket.  If it fails, report the failure by aborting and starting tracking anew.
				socket = new DatagramSocket();
				// Attempts to set the timeout.  If this fails, reports the failure by aborting and starting tracking anew.
				socket.setSoTimeout(socketTimeout);

				InetSocketAddress address = new InetSocketAddress(hostIP, udpPort);
				byte[] buf = new byte[0];
				DatagramPacket packet;
				// Attempts to create a data packet to send.  Same procedure for failure
				packet = new DatagramPacket(buf, 0, address);

				totalTransferred = SendAndReceive.udpSendPacket(socket, packet, xferLength);
				if (totalTransferred == xferLength) {
						// successful trial!
						TransferRate.stop("udp", totalTransferred);
				} else if (totalTransferred == -1) {
					Log.i("TAG", "Failed to send to the server");
					TransferRate.abort("udp", 0);
				} else {
					TransferRate.abort("udp", totalTransferred);
				}
			} catch (SocketException e) {
				e.printStackTrace();
				Log.w("TAG", "Preparing the UDP socket failed");
				TransferRate.abort("udp", totalTransferred);
			}
			// Sends a packet informing the server it is ready to receive, and checks if any packets are received.
			// If not, logs the failure and continues the trials.
		}
		return TransferRate.get("udp");
	}
	
	/**
	 * Performs nTrials trials via TCP of a data xfer to host hostIP on port tcpPort.  Expects to get xferLength
	 * bytes in total from that host/port.  Is willing to wait up to socketTimeout msec. for new data to arrive.
	 * @return A TransferRateInterval object that measured the total bytes of data received over all trials and
	 * the total time taken.  The measured time should include socket creation time.
	 */
	@Override
	public TransferRateInterval tcpDataXfer(String hostIP, int tcpPort, int socketTimeout, int xferLength, int nTrials) {
		int readInterval = 5;  // time in ms between each read operation.  Must be fast enough to prevent the received-packet buffer from overflowing.

		for (int i = 0; i < nTrials; i++) {
			TransferRate.start("tcp");
			int dataTransferred = 0;
			Socket socket = null;
			try {
				socket = new Socket();
				socket.setSoTimeout(socketTimeout);
				//  Attempts to create a TCP socket.  If this fails, aborts the timer and starts it again
				socket.connect(new InetSocketAddress(hostIP, tcpPort));
				InputStream is = socket.getInputStream();
				byte[] buf = new byte[xferLength];
				
				// Attempts to connect to the IP address.
				int totalBytesRead = 0;
				int readTimeLeft = socketTimeout; // Keep track of the total time reading
				while(totalBytesRead < xferLength && readTimeLeft > 0) {
					try {
						Thread.sleep(readInterval);
					} catch (InterruptedException e) {
						Log.i(TAG, "Socket read sleep timer interrupted");
						e.printStackTrace();
					}
					readTimeLeft -= readInterval;

					// read from the stream
					int bytesRead = is.read(buf, totalBytesRead, xferLength - totalBytesRead);

					// if the stream wasn't finished, then update byte counts
					if (bytesRead != -1) {
						dataTransferred += bytesRead;
						totalBytesRead += bytesRead;
					} else {
						break;
					}
				}
				if (dataTransferred == xferLength) {
					// trial successful!
					TransferRate.stop("tcp", dataTransferred);
				} else {
					TransferRate.abort("tcp", dataTransferred);
				}
			} catch (IOException e) {
				e.printStackTrace();
				TransferRate.abort("tcp", dataTransferred);
			} finally {
				try {
					socket.close();
					Log.d(TAG, "socket.close succeeded");
				} catch (IOException e) {
					e.printStackTrace();
					Log.i(TAG, "socket.close failed");
				}
			}
			
		}		
		
		return TransferRate.get("tcp");
	}
	
}
