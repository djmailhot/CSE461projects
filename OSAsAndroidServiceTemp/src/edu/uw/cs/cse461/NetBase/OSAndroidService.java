package edu.uw.cs.cse461.NetBase;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
import edu.uw.cs.cse461.NetBase.NetBase;
import edu.uw.cs.cse461.util.Log;

/**
 * This class makes the base OS code into an Android started service.  The purpose of that is to 
 * allow the OS to retain an ephemeral port for extended periods of time.  (Android activities are
 * destroyed and recreated easily (e.g., if the screen is rotated), resulting in rapid changes in
 * port number.)
 * @author zahorjan
 *
 */

public class OSAndroidService extends IntentService {
	private static final String TAG="OSAndroidService";
	
	private final IBinder mBinder = new LocalBinder();
	
	public class LocalBinder extends Binder {
		OSAndroidService getService() {
			return OSAndroidService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.e(TAG,"onBind: " + intent.toString());
		return mBinder;
	}
	
	public String testMethod() {
		return new String("testMethod in StartupService(" + android.os.Process.myPid() + ", " + android.os.Process.myTid() + ")");
	}

	  /** 
	   * A constructor is required, and must call the super IntentService(String)
	   * constructor with a name for the worker thread.
	   */
	  public OSAndroidService() {
	      super("OSAndroidService");
	  }
	  
	  
	  @Override
	  public int onStartCommand(Intent intent, int flags, int startId) {
		  Log.e(TAG,"onStartCommand: " + intent.toString());
	      Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
//	      return super.onStartCommand(intent,flags,startId);
	      
	      //Notification notice = new Notification(R.drawable.cse_hdpi, "CSE461 Service", System.currentTimeMillis());	
	      //if ( notice == null ) Log.e(TAG, "notice == null");
	      //else Log.e(TAG, "notice != null");
	      //startForeground(1234, notice);  // arbitrary notification id used in this call
	      
	      String configFilename = "jz.cse461.config.ini.png";  // .png avoids android compresing the file
	      try {
	    	 NetBase.theNetBase().init(getAssets().openFd(configFilename).createInputStream());
			} catch (Exception e) {
				Log.e(TAG, "Caught exception: " + e.getMessage());
			}
	 	    	  
	      return START_STICKY;
	  }
	  
	  @Override
	  public void onStart(Intent intent, int startId) {
		  Log.e(TAG,"onStart: " + intent.toString());
		  super.onStart(intent, startId);
	  }
	  
	  @Override
	  public void onCreate() {
		  Log.e(TAG, "onCreate: " + testMethod());
		  super.onCreate();
	  }
	  
	  @Override
	  public void onDestroy() {
		  Log.e(TAG, "onDestroy: " + testMethod());
		  super.onDestroy();
	  }

	  /**
	   * The IntentService calls this method from the default worker thread with
	   * the intent that started the service. When this method returns, IntentService
	   * stops the service, as appropriate.
	   */
	  @Override
	  protected void onHandleIntent(Intent intent) {
		  Log.e(TAG,"onHandleCommand: " + intent.toString());
	      // Normally we would do some work here, like download a file.
	      // For our sample, we just sleep for 5 seconds.
	      long endTime = System.currentTimeMillis() + 5*1000;
	      while (System.currentTimeMillis() < endTime) {
	          synchronized (this) {
	              try {
	                  wait(endTime - System.currentTimeMillis());
	              } catch (Exception e) {
	              }
	          }
	      }
	  }
}
