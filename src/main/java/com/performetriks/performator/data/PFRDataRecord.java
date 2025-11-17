package com.performetriks.performator.data;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.Unvalue;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRDataRecord {
	
	private static final Logger logger = (Logger) LoggerFactory.getLogger(PFRDataRecord.class);
	
	LinkedHashMap<String, Unvalue> keyValues = new LinkedHashMap<>();
	
	/***********************************************************************
	 * 
	 ***********************************************************************/
	public PFRDataRecord() {
	}
	
	/***********************************************************************
	 * Creates a new record based on the fields of a JsonObject.
	 ***********************************************************************/
	public PFRDataRecord(JsonObject object) {
		
		for(Entry<String, JsonElement> entry : object.entrySet()) {
			String name = entry.getKey();
			Unvalue unvalue = Unvalue.newFromJsonElement(object.get(name));
			
			this.add(name, unvalue);
			
		}
		
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has alread been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, Unvalue value) {
		keyValues.put(key, value);
		return this;
	}
	
	/***********************************************************************
	 * Removes a value for a key. If a value has alread been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return the previous value associated with key, or null if there was 
	 * no mapping for key.(A null return can also indicate that the map 
	 * previously associated null with key.)
	 ***********************************************************************/
	public Unvalue remove(String key) {
		return keyValues.remove(key);
	}
	
	/***********************************************************************
	 * Get the value for a specified key.
	 * 
	 * @param key
	 * 
	 * @return the value to which the specified key is mapped, or a Unvalue
	 * that is null if not available
	 ***********************************************************************/
	public Unvalue get(String key) {
		if(!keyValues.containsKey(key)) {
			String message = "Data Record did not contain a field with name: "+key;
			HSR.addWarnMessage(message);
			logger.warn(message);
			
			return Unvalue.newNull();
		}
		return keyValues.get(key);
	}
	
	/***********************************************************************
	 * Returns the number of key-value mappings in this data record.
	 * 
	 * @return size
	 ***********************************************************************/
	public int size() {
		return keyValues.size();
	}
	
	/***********************************************************************
	 * Returns the record as a string.
	 * 
	 * @return size
	 ***********************************************************************/
	public  String toString() {

		if(keyValues.size() == 0) { return ""; } 
		
		return new StringBuilder(" [")
				.append( Joiner.on(", ").withKeyValueSeparator("=").join(keyValues) )
				.append("]")
				.toString()
				;
	}
	
	

}
