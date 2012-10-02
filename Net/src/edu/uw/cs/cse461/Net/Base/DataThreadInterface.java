package edu.uw.cs.cse461.Net.Base;

/**
 * Interface for a thread that opens a network socket and waits for data to be sent through that socket.
 */
public interface DataThreadInterface extends Runnable {

	@Override
	public void run();

	/**
	 * Terminate and cleanup the Thread
	 */
	public void end();
}
