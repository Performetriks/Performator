package com.performetriks.performator.data;

import java.util.ArrayList;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.xresch.xrutils.data.XRRecord;


/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRDataSourceJsonArray extends PFRDataSourceStatic {

	JsonArray array;
	
	/*****************************************************************
	 * Creates a data source based on the given JsonArray.
	 * The data will be loaded once, changes to the array will
	 * not be reflected in the data source.
	 * 
	 * @param array the array to base the data source one.
	 *****************************************************************/
	public PFRDataSourceJsonArray(JsonArray array) {
		this(null, array);
	}
	/*****************************************************************
	 * Creates a data source based on the given JsonArray.
	 * The data will be loaded once, changes to the array will
	 * not be reflected in the data source.
	 * 
	 * @param uniqueName unique name for this data source
	 * @param array the array to base the data source one.
	 *****************************************************************/
	public PFRDataSourceJsonArray(String uniqueName, JsonArray array) {
		super(uniqueName);
		this.array = array;
	}

	/*****************************************************************
	 * This method should return a unique identifier for your data source.
	 * This is either the custom data source name, or a combination of
	 * values that uniquely identify the source.
	 * 
	 * @return String Datasource unique name
	 * 
	 *****************************************************************/
	public String getUniqueName() {
		
		if( Strings.isNullOrEmpty(uniqueName) ) {
			uniqueName = "JsonArray: " + array.hashCode()
						+ " | accessMode: " + accessMode()
						+ " | retainMode: " + retainMode()
						+ " | isShared:" + isShared()
						;
		}
		
		return uniqueName;
	}
	/*****************************************************************
	 * Loads the data.
	 * 
	 * @return list or records.
	 *****************************************************************/
	@Override
	protected ArrayList<XRRecord> load() {
		
		
		ArrayList<XRRecord> result = new ArrayList<>();
		
		if(array == null) { return result; }
		
		for(JsonElement e : array) {
			if(e.isJsonObject()) {
				result.add( new XRRecord(e.getAsJsonObject()) );
			}
		}
		
		return result;
	}

}
