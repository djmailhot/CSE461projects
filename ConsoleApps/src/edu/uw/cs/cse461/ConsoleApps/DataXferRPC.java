package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.ConsoleApps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.Net.RPC.RPCCall;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferRPC extends NetLoadableConsoleApp implements DataXferRPCInterface {
	private static final String TAG="DataXferRPC";
	private static final int DEFAULT_XFER_LENGTH = 1000;
	
	public DataXferRPC() {
		super("dataxferrpc", true);
	}

	/**
	 * Performs nTrials trials via RPC of a data xfer to host hostIP on specified port.  Expects to get xferLength
	 * bytes in total from that host/port.
	 * @return A TransferRateInterval object that measured the total bytes of data received over all trials and
	 * the total time taken.  The measured time should include socket creation time.
	 */
	@Override
	public TransferRateInterval DataXfer(String hostIP, int port, int xferLength, int nTrials) throws Exception {
		for (int i = 0; i < nTrials; i++) {
			System.out.println("trial number: " + i);
			TransferRate.start("DataXferRPC");
			int dataReceived = 0;
			
			try {
				JSONObject args = new JSONObject().put("xferLength", xferLength);
				JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc", "dataxfer", args);
				
				try {
					String encodedData = response.getString("data");
					byte[] data = Base64.decode(encodedData);
					dataReceived = data.length;
					TransferRate.stop("DataXferRPC", dataReceived);
				} catch (JSONException e) {
					TransferRate.abort("DataXferRPC", dataReceived);
					Log.i(TAG, "Could not extract 'data' payload from JSON response");
					e.printStackTrace();
				}
				
			} catch (IOException e) {
				TransferRate.abort("DataXferRPC", dataReceived);
				Log.i(TAG, "IO Exception on RPC call to dataxfer service");
				e.printStackTrace();
			} catch (JSONException e) {
				TransferRate.abort("DataXferRPC", dataReceived);
				Log.i(TAG, "JSON Exception on RPC call to dataxfer service");
				e.printStackTrace();
			}
		}
		return TransferRate.get("DataXferRPC");
	}

	/**
	 * This method is invoked each time the infrastructure is asked to launch this application.
	 */
	@Override
	public void run() throws Exception {
		
		try {

			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("dataxferrpc.server"); 
			if ( server == null ) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if ( server == null ) return;
				if ( server.equals("exit")) return;
			}

			int port = config.getAsInt("dataxferrpc.port", -1, TAG); 
			if ( port == -1 ) {
				System.out.print("Enter port number, or empty line to exit: ");
				String portStr = console.readLine();
				if ( portStr == null || portStr.trim().isEmpty() ) return;
				port = Integer.parseInt(portStr);
			}
			
			int nTrials = config.getAsInt("dataxferrpc.ntrials", -1, TAG);
			if ( nTrials == -1 ) {
				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				nTrials = Integer.parseInt(trialStr);
			}

			TransferRate.clear();
			int xferLength = DEFAULT_XFER_LENGTH;
			System.out.print("Enter number of bytes to transfer (or blank for default of "+DEFAULT_XFER_LENGTH+"): ");
			try {
				String line = console.readLine();
				if (!line.equals("")) {
					xferLength = Integer.parseInt(line);
				}
			} catch (NumberFormatException e) { // use default
				System.out.println("Sorry, format not recognized, using default length");
			} finally {
				System.out.println("Requesting transfer of "+xferLength);
			}

			//-----------------------------------------------------
			// RPC transfer
			//-----------------------------------------------------
			
			TransferRateInterval rpcStats = DataXfer(server, port, xferLength, nTrials);
			
			System.out.println("\nRPC: xfer rate = " + String.format("%9.0f", rpcStats.mean() * 1000.0) + " bytes/sec.");
			System.out.println("RPC: failure rate = " + String.format("%5.1f", rpcStats.failureRate()) +
					" [" + rpcStats.nAborted()+ "/" + rpcStats.nTrials() + "]");

						
		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}

}
