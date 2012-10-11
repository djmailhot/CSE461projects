package edu.uw.cs.cse461.Net.TCPMessageHandler;

import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.TCPMessageHandlerThread;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends NetLoadableService  {
	private static final String TAG="DataXferTCPMessageHandlerService";
	
	public static final int XFERSIZE = 1000;

	private int portNum;
	private TCPMessageHandlerThread tCPDataThread;
	
	public DataXferTCPMessageHandlerService() throws Exception {
		super("dataxfertcpmessagehandler", true);

		ConfigManager config = NetBase.theNetBase().config();
		portNum = config.getAsInt("dataxfertcpmessagehandler.port", 0, TAG);
		
		int timeout = config.getAsInt("dataxfertcpmessagehandler.sockettimeout");
		
		if (portNum == 0) throw new RuntimeException("dataxfertcpmessagehandler service can't run -- no dataxfertcpmessagehandler.port entry in config file");

		// Init data thread sockets
		tCPDataThread = new TCPMessageHandlerThread(portNum, XFERSIZE,timeout);
		
		startup();
	}

	/**
	 * Startup the service by opening sockets to listen on the intended ports.
	 */
	private void startup() {
		Thread thread = new Thread(tCPDataThread);
		thread.start();
	}
	
	/**
	 * This method is required in every RPCCallable class.  If this object has created any 
	 * threads, it should cause them to terminate.
	 */
	@Override
	public void shutdown() {
		Log.d(TAG, "Shutting down");
		// End each data thread socket before shutdown
		tCPDataThread.end();
		super.shutdown();
	}
	
	@Override
	public String dumpState() {
		return "DataXferTCPMessageHandlerService has no state";
	}

}
