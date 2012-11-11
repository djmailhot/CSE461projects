package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.ConsoleApps.PingInterface.PingRPCInterface;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.Net.RPC.RPCCall;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingRPC extends NetLoadableConsoleApp implements PingRPCInterface {
	private static final String TAG="PingRPC";

	public PingRPC() {
		super("pingrpc", true);
	}

	@Override
	public ElapsedTimeInterval ping(String hostIP, int port, int nTrials) throws Exception {
		
		for(int i = 0; i<nTrials; i++){
			try {		
				ElapsedTime.start("PingRPC");
				
				JSONObject args = new JSONObject().put("derp", "herp");
				JSONObject resultObj = RPCCall.invoke(hostIP, port,	"echorpc", "echo", args);
				
				ElapsedTime.stop("PingRPC");
			} catch (JSONException e) {
				ElapsedTime.abort("PingRPC");
				Log.i(TAG, "JSON exception on calling into RPC layer.");
				e.printStackTrace();
			} catch (IOException e) {
				ElapsedTime.abort("PingRPC");
				Log.i(TAG, "IO exception on calling into RPC layer.");
				e.printStackTrace();
			}
		}

		return ElapsedTime.get("PingRPC");
	}

	@Override
	public void run() throws Exception {
		try {
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				ElapsedTime.clear();

				String targetIP = config.getProperty("rpc.server");
				if ( targetIP == null ) {
					System.out.println("No rpc.server entry in config file.");
					System.out.print("Enter a host ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				int targetTCPPort = config.getAsInt("rpc.serverport", 0, TAG);
				if ( targetTCPPort == 0 ) {
					System.out.print("Enter the server's RPC port, or empty line to skip: ");
					String targetTCPPortStr = console.readLine();
					if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
					else targetTCPPort = Integer.parseInt(targetTCPPortStr);
				}

				
				int nTrials = config.getAsInt("ping.ntrials", 5, TAG);
				
				System.out.println("Host: " + targetIP);
				System.out.println("rpc port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval rpcResult = null;

				if ( targetTCPPort != 0 ) {
					rpcResult = ping(targetIP, targetTCPPort, nTrials);
				}
				
				if ( rpcResult != null ) System.out.println("RPC: " + String.format("%.2f msec", rpcResult.mean()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRPCHandler.run() caught exception: " +e.getMessage());
		}
	}

}
