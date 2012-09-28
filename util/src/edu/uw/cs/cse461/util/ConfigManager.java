package edu.uw.cs.cse461.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

/**
 * A class intended to make parsing the config.ini file easier.  
 * See ConfigManagerInterface for further documentation and a concise list of public methods.
 * @author zahorjan
 *
 */
public class ConfigManager extends Properties implements ConfigManagerInterface {
	
	/**
	 * Can't pass in the more natural config file name because Android won't tell us
	 * where it is (won't tell us the path to our assets).  Instead, caller must
	 * open the file.
	 * @param configFileInputStream  Attached to the config file.
	 * @throws IOException
	 */
	public ConfigManager(FileInputStream configFileInputStream) throws IOException {
		load(configFileInputStream);
	}
	
	/**
	 * Reads entries like x.submit.test.cse461.:IP:port
	 * @param fieldName
	 * @return
	 * @throws RuntimeException
	 */
	public Vector<String[]> readNameIPPortVec(String fieldName) throws RuntimeException {
		return readNameRecordVec(fieldName);
	}	
	
	/**
	 * Reads entries like jz.cse461.:password
	 * @param fieldName
	 * @return
	 */
	public Vector<String[]> readNamePasswordVec(String fieldName) throws RuntimeException {
		Vector<String[]> result = readParsedVector(fieldName);
		// each element should be a vector of length 2
		for ( String[] element : result ) {
			if ( element.length != 2 ) throw new RuntimeException("Error in config file entry " + fieldName + ".  Each entry should have 2 fields.");
		}
		return result;
	}
	
	/**
	 * Reads entries like a:jz.cse461.:password and cname:jz.cse461:foo.cse461:password
	 * @param fieldName
	 * @return
	 * @throws RuntimeException
	 */
	public Vector<String[]> readNameRecordVec(String fieldName) throws RuntimeException {
		Vector<String[]> result = readParsedVector(fieldName);
		// each element should be a vector of length 2
		for ( String[] element : result ) {
			if ( element.length != 3 &&
				 (element.length != 4 || !element[0].equals("cname"))
			   ) throw new RuntimeException("Illegal config entry for field " + fieldName );
		}
		return result;
	}
	
	/**
	 * Takes a space separated list of colon separated tokens and returns a Vector of String arrays.
	 * @param fieldName
	 * @return
	 * @throws RuntimeException
	 */
	private Vector<String[]> readParsedVector(String fieldName) throws RuntimeException {
		Vector<String[]> result = new Vector<String[]>();
		
		String rawString = this.getProperty(fieldName);
		if ( rawString == null ) return result;
		String[] strVec = rawString.split("[\\s]+");
		for (String entry : strVec ) {
			result.add(entry.split("[:]"));
		}
		return result;
	}	

	@Override
	public String[] getAsStringVec(String key) {
		String value = getProperty(key);
		if ( value == null ) return null;
		return value.split("[\\s]+");
	}
	
	@Override
	public int getAsInt(String key) throws NoSuchFieldException {
		String valStr = getProperty(key);
		if ( valStr == null ) throw new NoSuchFieldException("No " + key + " entry found in config file");
		return Integer.parseInt(valStr);
	}
	
	@Override
	public int getAsInt(String key, int defaultVal, String TAG) {
		return getAsInt(key, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE, TAG);
	}
	
	@Override
	public int getAsInt(String key, int defaultVal, int minimum, String TAG) {
		return getAsInt(key, defaultVal, minimum, Integer.MAX_VALUE, TAG);
	}
	
	@Override
	public int getAsInt(String key, int defaultVal, int minimum, int maximum, String TAG) {
		int result;
		try {
			result = getAsInt(key);
		} catch (Exception e) {
			Log.w(TAG, "Missing or non-integer value for config entry " + key + ".  Using default " + defaultVal);
			result = defaultVal;
		}
		
		if ( result < minimum ) {
			Log.w(TAG, key + " value " + result + " is below minimum (" + minimum + ").  Resetting to minimum.");
			result = minimum;
		}
		else if ( result > maximum ) {
			Log.w(TAG, key + " value " + result + " is above (" + maximum + ").  Resetting to maximum.");
			result = maximum;
		}
		return result;
	}
}
