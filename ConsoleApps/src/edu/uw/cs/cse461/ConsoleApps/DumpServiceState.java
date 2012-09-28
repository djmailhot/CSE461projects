package edu.uw.cs.cse461.ConsoleApps;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableConsoleApp;

/**
 * An application that prints the states of running services.  Intended as a debugging aide. 
 * @author zahorjan
 *
 */
public class DumpServiceState extends NetLoadableConsoleApp {

	public DumpServiceState() {
		super("dumpservicestate", true);
	}
	
	@Override
	public void run() {

		// Eclipse doesn't support System.console()
		BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

		while( true ) {

			try {
				System.out.println("Enter service name, or 'list' to list all service names, 'all' to dump all services, or 'exit' to exit: ");
				String serviceName = console.readLine();
				if ( serviceName.trim().isEmpty() ) continue;
				
				if ( serviceName.equals("exit")) break;
				
				if ( serviceName.equals("list") ) {
					for ( String name : NetBase.theNetBase().loadedServiceNames() ) {
						System.out.println("\t" + name);
					}
					continue;
				}
				
				List<String> serviceList;
				if ( serviceName.equals("all") ) serviceList = NetBase.theNetBase().loadedServiceNames();
				else {
					serviceList = new ArrayList<String>();
					serviceList.add(serviceName);
				}
				
				for ( String sName : serviceList ) {
					System.out.println( "\n" + sName + " Service:");
					NetLoadableService service = NetBase.theNetBase().getService(sName);
					if ( service != null ) System.out.println( "\t" + service.dumpState() );
					else System.out.println("\tNot loaded");
				}
			} catch (Exception e) {
				System.err.println("Caught exception: " + e.getMessage());
			}

		}
	}
	
}
