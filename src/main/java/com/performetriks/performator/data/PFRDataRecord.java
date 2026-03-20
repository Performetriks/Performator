package com.performetriks.performator.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
	
	public static final String LB = "${";
	public static final String RB = "}";

	public static final int LB_SIZE = LB.length();
	public static final int RB_SIZE = RB.length();
	
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
	 * Creates a new record based on the key-value pairs of a Map.
	 ***********************************************************************/
	public PFRDataRecord(Map<String, String> map, boolean convertTypes) {
		
		for(Entry<String, String> entry : map.entrySet()) {
			String name = entry.getKey();
			
			if(!convertTypes) {
				Unvalue unvalue = Unvalue.newString(map.get(name));
				this.add(name, unvalue);
			}else {
				Unvalue unvalue = Unvalue.newFromString(map.get(name));
				this.add(name, unvalue);
			}
			
		}
		
	}
	
	/***********************************************************************
	 * Creates a new record using the values in the map.
	 * 
	 ***********************************************************************/
	public PFRDataRecord(Map<String, Unvalue> keyValues) {
		
		this.keyValues.putAll(keyValues);
	}
	
	/***********************************************************************
	 * Create a clone of the data record.
	 * @return clone of this instance
	 ***********************************************************************/
	public PFRDataRecord clone() {
		return new PFRDataRecord(keyValues);
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
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
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, String value) {
		keyValues.put(key, Unvalue.newString(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, JsonObject value) {
		keyValues.put(key, Unvalue.newJson(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, JsonArray value) {
		keyValues.put(key, Unvalue.newJson(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, ArrayList<String> value) {
		keyValues.put(key, Unvalue.newFromStringArray(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, boolean value) {
		keyValues.put(key, Unvalue.newBoolean(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, int value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, long value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, float value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, double value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, short value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, Number value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds a value for a key. If a value has already been assigned 
	 * for that key it will be replaced.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord add(String key, BigDecimal value) {
		keyValues.put(key, Unvalue.newNumber(value));
		return this;
	}
	
	/***********************************************************************
	 * Adds all the key-value pairs of the record to this class.
	 * 
	 * @param record
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord addAll(PFRDataRecord record) {
		keyValues.putAll(record.keyValues);
		return this;
	}
	
	/***********************************************************************
	 * Adds all the key-value pairs of the map to this class.
	 * 
	 * @param map
	 * 
	 * @return instance for chaining
	 ***********************************************************************/
	public PFRDataRecord addAll(Map<String, Unvalue> map) {
		keyValues.putAll(map);
		return this;
	}
	
	
	/***********************************************************************
	 * 
	 * @param key
	 * @return true if the record contains a value for the given key
	 ***********************************************************************/
	public boolean containsKey(String key) {
		return keyValues.containsKey(key);
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
	 * Inserts the values of this data record into a string by replacing 
	 * placeholders. The placeholders syntax is "${fieldname}".
	 * This method is faster than using multiple String.replace() calls as  
	 * it only parses the string once from left to right.	 * 
	 * 
	 * @param insertHere
	 * @return string with replaced placeholders
	 ***********************************************************************/
	public String insert(String insertHere) {
		

		if(keyValues == null || keyValues.isEmpty()) {
			return insertHere;
		}

		StringBuilder sb = new StringBuilder(insertHere);
		
		int fromIndex = 0;
		int leftIndex = 0;
		int rightIndex = 0;
		int length = sb.length();

		while (fromIndex < length && leftIndex < length) {

			leftIndex = sb.indexOf(LB, fromIndex);

			if (leftIndex != -1) {
				rightIndex = sb.indexOf(RB, leftIndex);

				if (rightIndex != -1 && (leftIndex + LB_SIZE) < rightIndex) {

					String propertyName = sb.substring(leftIndex + LB_SIZE, rightIndex);
					if(keyValues != null 
					&& keyValues.containsKey(propertyName)) {
						sb.replace(leftIndex, rightIndex + RB_SIZE,
								keyValues.get(propertyName).getAsString());
					}
					// start again from leftIndex
					fromIndex = leftIndex + 1;

				} else {
					logger.trace("Parameter was missing the right bound");

					break;
				}

			} else {
				// no more stuff found to replace
				break;
			}
		}

		return sb.toString();

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
	 * Returns a clone of the HashMap that holds this record's key-value pairs.
	 * 
	 * @return map
	 ***********************************************************************/
	public LinkedHashMap<String, Unvalue> toHashMap() {
		return new LinkedHashMap<>(keyValues);
	}
	
	/***********************************************************************
	 * Converts the key-value pairs of this record to a JsonObject.
	 * 
	 * @return JsonObject
	 ***********************************************************************/
	public JsonObject toJsonObject() {
		JsonObject object = new JsonObject();
		
		for(Entry<String, Unvalue> entry : keyValues.entrySet()) {
			object.add(entry.getKey(), entry.getValue().getAsJsonElement());
		}
		
		return object;
	}
	
	/***********************************************************************
	 * Returns the record as an array-like string oneliner.
	 * Useful for debugging.
	 * @return string
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
	 * Returns the record as an array-like string with pretty print.
	 * Useful for debugging.
	 * 
	 * @return string
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
