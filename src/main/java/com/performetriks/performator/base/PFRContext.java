package com.performetriks.performator.base;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.google.common.base.Joiner;
import com.performetriks.performator.data.PFRDataRecord;
import com.xresch.hsr.utils.Unvalue;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRContext {
	
	//------------------------------
	// Scope Global 
	private static PFRTest globalTest;
	
	//------------------------------
	// Scope User 
	private static ThreadLocal<LinkedHashMap<String,String>> userLogDetails = ThreadLocal.withInitial(() -> new LinkedHashMap<String,String>());
	
	/*****************************************************************
	 * Scope: Global<br>
	 * Set the test instance
	 *****************************************************************/
	protected static void test(PFRTest test) {
		globalTest = test;
	}
	
	/*****************************************************************
	 * Scope: Global<br>
	 * Get the test instance.
	 *****************************************************************/
	protected PFRTest test() {
		return globalTest;
	}
	
	/*****************************************************************
	 * Scope: User<br>
	 * Adds details that can be used by logging to add more useful 
	 * information to analyze failing data constellations.
	 *****************************************************************/
	public static void logDetailsAdd(String key, String value) {
		userLogDetails.get().put(key, value);
	}
	
	/*****************************************************************
	 * Scope: User<br>
	 * Adds all the fields of the record as details that can be used 
	 * by logging to add more useful information to analyze failing 
	 * data constellations.
	 *****************************************************************/
	public static void logDetailsAdd(PFRDataRecord record) {
		for(Entry<String, Unvalue> entry : record.entrySet()) {
			logDetailsAdd(entry.getKey(), entry.getValue().getAsString());
		}
	}
	
	/*****************************************************************
	 * Scope: User<br>
	 * Removes a log detail that was added previously.
	 *****************************************************************/
	public static void logDetailsRemove(String key) {
		userLogDetails.get().remove(key);
	}
	
	/*****************************************************************
	 * Scope: User<br>
	 * Creates a log details string starting with a blank for easier
	 * concatenation, like " [key=value, key2=value2 ...]".
	 * 
	 * @returns string or empty string
	 *****************************************************************/
	public static String logDetailsString() {
		LinkedHashMap<String,String> details = userLogDetails.get();
		
		if(details.size() == 0) { return ""; } 
		
		return PFR.JSON.toJSON(details).replace("\",\"", "\",   \"");
		
//		return new StringBuilder(" [")
//				.append( Joiner.on(", ").withKeyValueSeparator("=").join(details) )
//				.append("]")
//				.toString()
//				;
	}
	
	/*****************************************************************
	 * Scope: User<br>
	 * Returns a clone of the current log details.
	 *****************************************************************/
	public static LinkedHashMap<String,String> logDetails() {
		return new LinkedHashMap<String,String>(userLogDetails.get());
	}
}
