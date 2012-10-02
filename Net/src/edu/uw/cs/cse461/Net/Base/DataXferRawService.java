package edu.uw.cs.cse461.Net.Base;

import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;

/**
 * Transfers reasonably large amounts of data to client over raw TCP and UDP sockets.  In both cases,
 * the server simply sends as fast as it can.  The server does not implement any correctness mechanisms,
 * so using UDP clients may not receive all the data sent.
 * <p>
 * Four consecutive ports are used to send fixed amounts of data of various sizes.
 * <p>
 * @author zahorjan
 *
 */
public class DataXferRawService extends NetLoadableService  {
	private static final String TAG="DataXferRawService";
	
	public static final int NPORTS = 4;
	public static final int[] XFERSIZE = {1000, 10000, 100000, 1000000};

	private int mBasePort;
	private DataThreadInterface[] uDPDataThreads;
	private DataThreadInterface[] tCPDataThreads;
	
	public DataXferRawService() throws Exception {
		super("dataxferraw", true);

		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.baseport", 0, TAG);
		if ( mBasePort == 0 ) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.baseport entry in config file");

		// Init socket-listening threads
		uDPDataThreads = new DataThreadInterface[NPORTS];
		tCPDataThreads = new DataThreadInterface[NPORTS];
		for(int i=0; i < NPORTS; i++) {
			int portNum = mBasePort + i;
			uDPDataThreads[i] = new UDPDataThread(portNum, XFERSIZE[i]);
			tCPDataThreads[i] = new TCPDataThread(portNum, XFERSIZE[i]);
		}
		
		startup();
	}

	/**
	 * Startup the service by opening sockets to listen on the intended ports.
	 */
	private void startup() {
		for(int i=0; i < NPORTS; i++) {
			uDPDataThreads[i].run();
			tCPDataThreads[i].run();
		}
	}
	
	/**
	 * This method is required in every RPCCallable class.  If this object has created any 
	 * threads, it should cause them to terminate.
	 */
	@Override
	public void shutdown() {
		Log.d(TAG, "Shutting down");
		for(int i=0; i < NPORTS; i++) {
			uDPDataThreads[i].end();
			tCPDataThreads[i].end();
		}
		super.shutdown();
	}
	
	@Override
	public String dumpState() {
		return "DataXferRawService has no state";
	}
}
