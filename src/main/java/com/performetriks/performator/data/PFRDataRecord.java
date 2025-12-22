package com.performetriks.performator.data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
	 * Overloaded method to simplify working with enumerations.
	 * This method will call the Object.toString() method and calls the 
	 * this.get(String) method.
	 * 
	 * @param key
	 * 
	 * @return the value to which the specified key is mapped, or a Unvalue
	 * that is null if not available
	 ***********************************************************************/
	public Unvalue get(Object key) {
		if(key == null) { return Unvalue.newNull(); };
		return get(key.toString());
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
			logger.warn(message);
			
			return Unvalue.newNull();
		}
		return keyValues.get(key);
	}
	
	/***********************************************************************
	 * Get the value for a specified key as a string value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return String value or null
	 ***********************************************************************/
	public String getString(Object key) {
		return get(key).getAsString();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an integer value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Integer value or null
	 ***********************************************************************/
	public Integer getInteger(Object key) {
		return get(key).getAsInteger();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an Long value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Long value or null
	 ***********************************************************************/
	public Long getLong(Object key) {
		return get(key).getAsLong();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an Double value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Double value or null
	 ***********************************************************************/
	public Double getDouble(Object key) {
		return get(key).getAsDouble();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an Float value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Float value or null
	 ***********************************************************************/
	public Float getFloat(Object key) {
		return get(key).getAsFloat();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an Number value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Number value or null
	 ***********************************************************************/
	public Number getNumber(Object key) {
		return get(key).getAsNumber();
	}
	
	
	/***********************************************************************
	 * Get the value for a specified key as an BigDecimal value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return BigDecimal value or null
	 ***********************************************************************/
	public BigDecimal getBigDecimal(Object key) {
		return get(key).getAsBigDecimal();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as an boolean value.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return Boolean value 
	 ***********************************************************************/
	public Boolean getBoolean(Object key) {
		return get(key).getAsBoolean();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as a JsonArray.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return JsonArray value 
	 ***********************************************************************/
	public JsonArray getJsonArray(Object key) {
		return get(key).getAsJsonArray();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as a JsonObject.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return JsonObject value 
	 ***********************************************************************/
	public JsonObject getJsonObject(Object key) {
		return get(key).getAsJsonObject();
	}
	
	/***********************************************************************
	 * Get the value for a specified key as a JsonElement.
	 * 
	 * @param key name of value, key.toString() will be used to retrieve the value
	 * @return JsonElement value 
	 ***********************************************************************/
	public JsonElement getJsonElement(Object key) {
		return get(key).getAsJsonElement();
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
	 * Returns the entry set of key-value mappings in this data record.
	 * 
	 * @return entrySet
	 ***********************************************************************/
	public Set<Entry<String, Unvalue>> entrySet() {
		return keyValues.entrySet();
	}
	
	/***********************************************************************
	 * Returns the record as an array string oneliner.
	 * 
	 * @return size
	 ***********************************************************************/
	public  String toString() {

		if(keyValues.size() == 0) { return ""; } 
		
		return new StringBuilder("[")
				.append( Joiner.on("\",    ").withKeyValueSeparator("=\"").join(keyValues) )
				.append("\"]")
				.toString()
				;
		
	}
	
	/***********************************************************************
	 * Returns the record as an array string with pretty print.
	 * 
	 * @return size
	 ***********************************************************************/
	public  String toStringPretty() {

		if(keyValues.size() == 0) { return ""; } 
		
		return new StringBuilder("[\r\n  ")
				.append( Joiner.on("\"\r\n, ").withKeyValueSeparator("=\"").join(keyValues) )
				.append("\"\r\n]")
				.toString()
				;
		
	}
	
	
	

}
