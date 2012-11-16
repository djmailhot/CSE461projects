package edu.uw.cs.cse461.Net.DDNS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TimerTask;

import java.util.Timer;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.HTTP.HTTPProviderInterface;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.Net.DDNS.DDNSRRecord.ARecord;
import edu.uw.cs.cse461.Net.RPC.RPCCall;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;


public class DDNSResolverService extends NetLoadableService implements HTTPProviderInterface, DDNSResolverServiceInterface {
	private static final String TAG="DDNSResolverService";
	private static final int RESOLVER_EXCEPTION_RETRY_LIMIT = 5; // number of retries for DDNS resolution after runtime exceptions
	private static final Long CACHE_RECORD_TTL = (long)30000; // in ms.  Time to live for cached records
	private static final int REREGISTER_LIFETIME_BUFFER = 10000; // in ms.  Buffer of time between reregister attempt and lifetime limit
	
	private final ARecord rootRecord;
	private final ARecord hostRecord;
	private final String ddnsPassword;
	private final int resolveTTL;
	private final ResolverCache resolverCache;
	private final RegistrationScheduler registrationScheduler;

	/**
	 * Called to end execution.  Specifically, need to terminate any threads we've created.
	 */
	@Override
	public void shutdown() {
		registrationScheduler.unscheduleAll();
		try {
			this.unregister(new DDNSFullName(NetBase.theNetBase().hostname()));
		} catch (DDNSException e) {
			Log.w(TAG, e.getMessage());
			e.printStackTrace();			
		} catch (JSONException e) {
			Log.w(TAG, "Unregister failed. Maybe.");
			e.printStackTrace();			
		}
		super.shutdown();
	}
		
	/**
	 * Serves web pages.  The 0th element of uriArray is always null.
	 * The next element names this service ("ddnsresolver").  The optional third
	 * component is a name to be resolved before dumping the cache.
	 */
	@Override
	public String httpServe(String[] uriArray) {
		StringBuilder sb = new StringBuilder();
		sb.append("Host:  ").append(NetBase.theNetBase().hostname()).append("\n");
		if ( uriArray.length > 2 ) {
			sb.append("Resolving: ").append(uriArray[2]).append("\n");
			// third component
			
			//  resolve uriArray[2] to an address and append the address to the stringbuilder
			// ....
			sb.append("You haven't updated DDNSResolverServer.httpServer()");
		}
		// add any additional information you want here
		return sb.toString();
	}
	
	
	public DDNSResolverService() throws DDNSException {
		super("ddnsresolver", true);
		ConfigManager config = NetBase.theNetBase().config();

		this.resolveTTL = Integer.parseInt(config.getProperty("ddnsresolver.serverttl"));
		this.ddnsPassword = config.getProperty("ddnsresolver.password");

		String rootIp = config.getProperty("ddns.rootserver"); 
		int rootPort = Integer.parseInt(config.getProperty("ddns.rootport")); 
		this.rootRecord = new DDNSRRecord.SOARecord(rootIp, rootPort);

		String ip = NetBase.theNetBase().myIP();
		String port = config.getProperty("rpc.serverport");
		this.hostRecord = new DDNSRRecord.ARecord(ip, Integer.parseInt(port));

		this.registrationScheduler = new RegistrationScheduler(REREGISTER_LIFETIME_BUFFER);
		this.resolverCache = new ResolverCache(CACHE_RECORD_TTL);


		DDNSFullNameInterface fullName = new DDNSFullName(NetBase.theNetBase().hostname()); 
		register(fullName, this.hostRecord.port());
	}

	/**
	 * Unregisters a name.  
	 * @param name
	 * @throws DDNSException
	 */
	@Override
	public void unregister(DDNSFullNameInterface name) throws DDNSException, JSONException {
		JSONObject args = new JSONObject()
			.put("name", name.toString())
			.put("password", ddnsPassword);
		JSONObject response = invokeDDNSService("unregister", args);

		registrationScheduler.unscheduleReregister(name);
	}
	
	/**
	 * Registers a name as being on this host (IP) at the given port.
	 * If the name already exists, update its address mapping.  If it doesn't exist, create it (as an ARecord).
	 * @param name
	 * @param ip
	 * @param port
	 * @throws DDNSException
	 */
	@Override
	public void register(DDNSFullNameInterface name, int port) throws DDNSException {
		JSONObject args;
		try {
			args = new JSONObject()
				.put("name", name.toString())
				.put("ip", hostRecord.ip())
				.put("port", port)
				.put("password", ddnsPassword);
			
			JSONObject response = invokeDDNSService("register", args);
			JSONObject node = response.getJSONObject("node");
			
			long lifetime = response.getInt("lifetime") * 1000;
			Log.d(TAG, "scheduling reregistration with lifetime of "+lifetime+"ms of "+name);
			registrationScheduler.scheduleReregister(name, port, lifetime);

		} catch (JSONException e) {
			Log.w(TAG, "JSONException during registration for "+name);
			e.printStackTrace();
		}
	}

	
	/**
	 * Resolves a name to an ARecord containing an address.  Throws an exception if no ARecord w/ address can be found.
	 * @param name
	 * @return The ARecord for the name, if one is found
	 * @throws DDNSException
	 */
	@Override
	public ARecord resolve(String nameStr) throws DDNSException, JSONException {
		ARecord resultRecord = null;

		resultRecord = resolverCache.get(nameStr);

		if (resultRecord == null) {
			// invoke the name resolver
			JSONObject args = new JSONObject().put("name", nameStr);
			JSONObject response = invokeDDNSService("resolve", args);

			JSONObject node = response.getJSONObject("node");

			String type = node.getString("type");
			if (type.equals("A") || type.equals("SOA")) {
				resultRecord = parseRecord(node);
				resolverCache.put(nameStr, resultRecord);

				String name = node.getString("name");
				// If the names don't match, we're probably asking for a CNAME
				if (!name.equals(nameStr)) {
					resolverCache.put(name, resultRecord);
				}
			} else {
				// SOMETHING WENT WRONG
				// this is not an expected result
				String errorMessage = "DDNS resolve result for "+nameStr+" returned invalid node "+node;
				Log.w(TAG, errorMessage);
				throw new DDNSException.DDNSRuntimeException(errorMessage);
			}
		} else {
			Log.d(TAG, "cache contained record "+resultRecord);
		}
		return resultRecord;
	}
	
	
	@Override
	public String dumpState() {
		return "whatever you want";
	}


	/**
	 * Invoke the DDNS service
	 *
	 * @param method the specific DDNS method to invoke
	 * @param args the invocation arguments
	 */
	private JSONObject invokeDDNSService(String method, JSONObject args) throws DDNSException {
		boolean done = false;
		JSONObject response = null;
		String targetIp = rootRecord.ip();
		int targetPort = rootRecord.port();
		
		int steps = 0;
		int numExceptions = 0;
		try {
			do {
				String targetName = args.getString("name");
				steps++;
				// Did we take it to the limit?  The limit on resolve steps to take?
				if(steps > resolveTTL) {
					// SAFETY IS NOT GUARANTEED
					throw new DDNSException.DDNSTTLExpiredException(new DDNSFullName(targetName));
				}


				Log.d(TAG, "rpc "+method+" request with args "+args);
				// invoke the name resolver
				response = RPCCall.invoke(targetIp, targetPort, "ddns", method, args);


	            Log.d(TAG, "response payload of "+response);
				try {
					// check if something is wrong with the response.
					if (!response.has("resulttype")) {
						// SOMETHING WENT WRONG
						// this is not an expected result
						String errorMessage = "RPC result contains unexpected content "+response;
						Log.w(TAG, errorMessage);
						throw new DDNSException.DDNSRuntimeException(errorMessage);
					} else if (response.getString("resulttype").equals("ddnsexception")) {
						// response is a DDNS exception
						throw parseDDNSException(response);
					}
					done = response.getBoolean("done");
		
					// If we are not done, unpack the attached node, update our target,
					// and make another resolution call
					if (!done) {
						// parse the node representation
						JSONObject node = response.getJSONObject("node");
						String type = node.getString("type");
						// continue with resolving
						if (type.equals("NS")) {
							// update the target ip/port
							targetIp = node.getString("ip");
							targetPort = node.getInt("port");
						} else if (type.equals("CNAME")) {
							// resolve the name alias
							args.put("name", node.getString("alias"));
							targetIp = rootRecord.ip();
							targetPort = rootRecord.port();
						} else {
							// SOMETHING WENT WRONG
							// this is not an expected result
							String errorMessage = "DDNS intermediate resolution result returned invalid node "+node;
							Log.w(TAG, errorMessage);
							throw new DDNSException.DDNSRuntimeException(errorMessage);
						}
					}
				} catch(DDNSException.DDNSRuntimeException re) {
					// Allow a certain number of immediate retries for runtime exceptions
					numExceptions++;
					// If we reached the retry limit for exceptions, then actually throw the exception.
					if (numExceptions >= RESOLVER_EXCEPTION_RETRY_LIMIT) {
						Log.e(TAG, re.getMessage());
						throw re;
					}
					Log.i(TAG, "Attempting resolution retry after DDNSRuntime failure.");
					continue;
				}
			} while (!done);
		} catch (JSONException e) {
			Log.w(TAG, "JSONException stopped name resolution for "+method+" request with args "+args);
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, "IOException stopped name resolution for "+method+" request with args "+args);
			e.printStackTrace();
		}

		return response;
	}

	/**
	 * Parse the specified JSON bundle into a ARecord node, or null if not valid
	 */
	private ARecord parseRecord(JSONObject node) throws JSONException {
		String type = node.getString("type");
		if (type.equals("A")) {
			return new DDNSRRecord.ARecord(node);
		} else if (type.equals("NS")) {
			return new DDNSRRecord.NSRecord(node);
		} else if (type.equals("SOA")) {
			return new DDNSRRecord.SOARecord(node);
		}
		return null;
	}

	/**
	 * Parse the specified JSON bundle into DDNSException
	 */
	private DDNSException parseDDNSException(JSONObject response) throws JSONException{
		String message = response.getString("message");
		DDNSFullName name = new DDNSFullName(response.getString("name"));

		Log.i(TAG, "Exception returned with message: " + message);

		switch (response.getInt("exceptionnum")) {
			case 1: Log.w(TAG, "Specified name "+name+" not found");
					return new DDNSException.DDNSNoSuchNameException(name);
			case 2: Log.w(TAG, "node "+name+" has no valid address");
					return new DDNSException.DDNSNoAddressException(name);
			case 3: Log.w(TAG, "Password invalid for DDNS registration/unregistration");
					return new DDNSException.DDNSAuthorizationException(name);
			case 4: Log.e(TAG, "DDNS Runtime failure");
					return new DDNSException.DDNSRuntimeException(message);
			case 5: Log.w(TAG, "registration timeout expired for node "+name);
					return new DDNSException.DDNSTTLExpiredException(name);
			case 6: DDNSFullName zone = new DDNSFullName(response.getString("zone"));
					Log.w(TAG, "Specified name "+name+" not found in zone "+zone);
					return new DDNSException.DDNSZoneException(name, zone);
			default: return new DDNSException(message);
		}
	}


	/**
	 * Schedules reregistration of DDNS names based on provided lifetimes
	 */
	private class RegistrationScheduler {
		private final int reregisterLifetimeBuffer;
		private final Timer reregisterTimer;
		private final Map<DDNSFullNameInterface, TimerTask> scheduledTimerTaskMap;

		/**
		 * Constructs a scheduler to reregister DDNS nodes before each node's
		 * assigned lifetime expires.  It is intended that nodes have no
		 * downtime, so reregistration should occur before the lifetime expires.
		 *
		 * @param reregisterLifetimeBuffer a buffer of time in ms before the
		 * estimated end of the lifetime at which the reregister attempt occurs.
		 */
		public RegistrationScheduler(int reregisterLifetimeBuffer) {
			this.reregisterLifetimeBuffer = reregisterLifetimeBuffer;
			this.reregisterTimer = new Timer();
			this.scheduledTimerTaskMap = new HashMap<DDNSFullNameInterface, TimerTask>();
		}

		/**
		 * Schedules a DDNS name to be reregistered before the specified lifetime
		 * comes to pass.
		 */
		private synchronized void scheduleReregister(final DDNSFullNameInterface name, final int port, long lifetime) {
			// subtract the timeout buffer from the lifetime to ensure we
			// timeout in time to reregister before we die.  Have to take into
			// account network travel time and compute time.
			long timeout = lifetime - reregisterLifetimeBuffer;
			timeout = timeout < 0 ? 0 : timeout;

			TimerTask task = new TimerTask() {
				@Override
				public void run() {
					try {
						register(name, port);
					} catch (DDNSException e) {
						Log.w(TAG, "DDNSException no reregistration of "+name);
						e.printStackTrace();
					}
				}
			};
			unscheduleReregister(name);
			
			reregisterTimer.schedule(task, timeout);
			scheduledTimerTaskMap.put(name, task);
		}

		/**
		 * Unschedules the specified DDNS name from being reregistered.
		 */
		private synchronized void unscheduleReregister(DDNSFullNameInterface name) {
			TimerTask task = scheduledTimerTaskMap.get(name);
			if (task != null) {
				task.cancel();
				scheduledTimerTaskMap.remove(name);
			}
		}

		/**
		 * Unschedules all DDNS nodes from being reregistered.
		 */
		private synchronized void unscheduleAll() {
			reregisterTimer.purge();
		}
	}


	/**
	 * Cache for DDNS records recently resolved
	 */
	private class ResolverCache {
		private final Map<String, ARecord> records;
		private final Map<String, Long> timestamps;
		private final Long recordTTL;

		/**
		 * Construct cache for DDNS records with specified cache Time To Live
		 */
		public ResolverCache(Long recordTTL) {
			this.recordTTL = recordTTL;
			this.records = new HashMap<String, ARecord>();
			this.timestamps = new HashMap<String, Long>();
		}

		public ARecord get(String key) {
			Long currTime = System.currentTimeMillis();
			Long cacheTime;
			ARecord record = null;
			synchronized(this) {
				// If we are not in the cache
				if (!timestamps.containsKey(key) || !records.containsKey(key)) {
					return null;
				}
				cacheTime = timestamps.get(key);
				record = records.get(key);
			}

			// If the record is stale
			if (currTime > cacheTime + recordTTL) {
				Log.v(TAG, "DDNS record "+key+" evicted from cache after recordTTL");
				remove(key);
				return null;
			}
			return record;
		}

		public void put(String key, ARecord value) {
			Long currTime = System.currentTimeMillis();
			synchronized(this) {
				records.put(key, value);
				timestamps.put(key, currTime);
			}
		}

		private synchronized void remove(String key) {
			records.remove(key);
			timestamps.remove(key);
		}
	}

}
