package edu.uw.cs.cse461.ConsoleApps;

import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;

/**
 * A ConsoleApp that prints a version number for this software.
 * 
 * @author zahorjan
 *
 */
public class Version extends NetLoadableConsoleApp {
	
	/**
	 * OSConsoleApp's must have a constructor taking no arguments.  The constructor can initialize,
	 * but shouldn't perform any of the function of the app.  That's done in the run() method.
	 */
	public Version() {
		super("version", true);
	}

	/**
	 * This method will be called each time the app is invoked (by the AppManager).
	 */
	@Override
	public void run() {
		System.out.println("Version " + NetBase.theNetBase().version());
	}
}
