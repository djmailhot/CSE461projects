package edu.uw.cs.cse461.Net.Base;

import java.util.Scanner;


public class _SocketThreadTest{
	public static void main(String args[]) throws InterruptedException{
		UDPDataThread t = new UDPDataThread(11113,1000000);
		
		new Thread(t). start ( );
		
		Thread.sleep(500);
		System.out.println("<<<<Press any key to kill server>>>>");
		Scanner scanIn = new Scanner(System.in);
	    String sWhatever = scanIn.nextLine();
		scanIn.close();
		t.end();
		System.out.println("It done been killed.");
	}
}