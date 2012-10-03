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
import java.util.Timer;
import java.util.TimerTask;

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

//				TransferRateInterval udpStats = udpDataXfer(server, port, socketTimeout, xferLength, nTrials);
//				
//				System.out.println("UDP: xfer rate = " + String.format("%9.0f", udpStats.mean() * 1000.0) + " bytes/sec.");
//				System.out.println("UDP: failure rate = " + String.format("%5.1f", udpStats.failureRate()) +
//						           " [" + udpStats.nAborted() + "/" + udpStats.nTrials() + "]");

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
		TransferRate.start("udp");
		for (int i = 0; i < nTrials; i++) {
			DatagramSocket socket;	
			try {
				socket = new DatagramSocket();
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
			try {
				socket.setSoTimeout(socketTimeout);
			} catch (SocketException e) {
				e.printStackTrace();
				break;
			}
			InetSocketAddress address = new InetSocketAddress(hostIP, udpPort);
			byte[] buf = new byte[0];
			DatagramPacket packet;
			try {
				packet = new DatagramPacket(buf, 0, address);
			} catch (SocketException e) {
				e.printStackTrace();
				break;
			}
			if (SendAndReceive.udpSendPacket(socket, packet, 0) == null) {
				Log.w("DataXferRaw", "Failed to receive a response from the server");
			}
		}
		TransferRate.stop("udp", 1);
		return TransferRate.get("udp");
	}
	
	/**
	 * Performs nTrials trials via UDP of a data xfer to host hostIP on port udpPort.  Expects to get xferLength
	 * bytes in total from that host/port.  Is willing to wait up to socketTimeout msec. for new data to arrive.
	 * @return A TransferRateInterval object that measured the total bytes of data received over all trials and
	 * the total time taken.  The measured time should include socket creation time.
	 */
	@Override
	public TransferRateInterval tcpDataXfer(String hostIP, int tcpPort, int socketTimeout, int xferLength, int nTrials) {
		TransferRate.start("tcp");
		int attemptTimeout = 5;

		for (int i = 0; i < nTrials; i++) {
			System.out.println(i);
			Socket socket = null;
			try {
				socket = new Socket();
				try {
					socket.setSoTimeout(socketTimeout);
				} catch (SocketException e) {
					e.printStackTrace();
					break;
				}
				socket.connect(new InetSocketAddress(hostIP, tcpPort));
				InputStream is = socket.getInputStream();
				byte[] buf = new byte[xferLength];
				
				int bytesRead = 0;
				int totalAttempts = socketTimeout / attemptTimeout;
				int numAttempts = 0;
				while(bytesRead != -1 && bytesRead < xferLength && numAttempts < totalAttempts) {
					Thread.sleep(attemptTimeout);
					numAttempts++;
					Log.d(TAG, ""+bytesRead);
					bytesRead += is.read(buf, bytesRead, xferLength - bytesRead);
				}
				Log.d(TAG, "Reading Complete");
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				Log.w(TAG, "Socket sleep timer interrupted");
				e.printStackTrace();
			} finally {
				try {
					socket.close();
					Log.d(TAG, "socket.close called");
				} catch (IOException e) {
					e.printStackTrace();
					Log.w(TAG, "socket.close failed");
				}
			}
			
		}		
		
		TransferRate.stop("tcp", 1);
		return TransferRate.get("tcp");
	}
	
}
