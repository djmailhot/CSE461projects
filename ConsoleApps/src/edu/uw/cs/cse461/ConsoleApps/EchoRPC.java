package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.json.JSONObject;

import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.Net.RPC.RPCCall;
import edu.uw.cs.cse461.util.ConfigManager;

public class EchoRPC extends NetLoadableConsoleApp {
	private static final String TAG="EchoRPC";
	
	// ConsoleApp's must have a constructor taking no arguments
	public EchoRPC() {
		super("echorpc", true);
	}
	
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			String targetIP = config.getProperty("echorpc.server");
			if ( targetIP == null ) {
				System.out.println("No echorpc.server entry in config file.");
				System.out.print("Enter a host ip, or empty line to exit: ");
				targetIP = console.readLine();
				if ( targetIP == null || targetIP.trim().isEmpty() ) return;
			}

			int targetRPCPort = config.getAsInt("echorpc.port", 0, TAG);
			if ( targetRPCPort == 0 ) {
				System.out.print("Enter the server's TCP port, or empty line to exit: ");
				String targetTCPPortStr = console.readLine();
				if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) return;
				else targetRPCPort = Integer.parseInt(targetTCPPortStr);
			}
			
			while ( true ) {
				try {

					System.out.print("Enter message to be echoed, or empty line to exit: ");
					String msg = console.readLine();
					if ( msg.isEmpty() ) return;
					
					JSONObject response = RPCCall.invoke(targetIP, targetRPCPort, "echorpc", "echo", new JSONObject().put("msg", msg) );
					
					if ( response.has("msg") ) System.out.println(response.getString("msg"));
					else System.out.println("No msg returned!?  (No msg sent?)");
					
				} catch (Exception e) {
					System.out.println("Exception: " + e.getMessage());
				} 
			}
		} catch (Exception e) {
			System.out.println("Echo.run() caught exception: " +e.getMessage());
		}
	}
}
