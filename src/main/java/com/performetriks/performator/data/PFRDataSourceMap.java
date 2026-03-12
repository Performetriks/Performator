package com.performetriks.performator.data;

import java.util.ArrayList;
import java.util.Map;

import com.google.common.base.Strings;


/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRDataSourceMap extends PFRDataSourceStatic {

	private ArrayList<Map<String,String>> mapArray;
	private boolean convertTypes = true;
	
	/*****************************************************************
	 * Creates a data source based on the given JsonArray.
	 * The data will be loaded once, changes to the array will
	 * not be reflected in the data source.
	 * 
	 * @param mapArray the array to base the data source on.
	 *****************************************************************/
	public PFRDataSourceMap(ArrayList<Map<String,String>> mapArray) {
		this(null, mapArray, true);
	}
	
	/*****************************************************************
	 * Creates a data source based on the given JsonArray.
	 * The data will be loaded once, changes to the array will
	 * not be reflected in the data source.
	 * 
	 * @param mapArray the array to base the data source on.
	 * @param convertTypes if data type conversion should be done. E.g.
	 *        a number String will be converted to a number, a boolean
	 *        String to boolean etc... This will save conversion time 
	 *        later.
	 *****************************************************************/
	public PFRDataSourceMap(ArrayList<Map<String,String>> mapArray, boolean convertTypes) {
		this(null, mapArray, convertTypes);
	}
	/*****************************************************************
	 * Creates a data source based on the given JsonArray.
	 * The data will be loaded once, changes to the array will
	 * not be reflected in the data source.
	 * 
	 * @param uniqueName unique name for this data source
	 * @param mapArray the array to base the data source on.
	 * @param convertTypes if data type conversion should be done. E.g.
	 *        a number String will be converted to a number, a boolean
	 *        String to boolean etc... This will save conversion time 
	 *        later.
	 *****************************************************************/
	public PFRDataSourceMap(String uniqueName, ArrayList<Map<String,String>> mapArray, boolean convertTypes) {
		super(uniqueName);
		this.mapArray = mapArray;
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
		
		if(!Strings.isNullOrEmpty(uniqueName)) {
			uniqueName = "" + mapArray.hashCode()
						+ "-" + accessMode()
						+ "-" + retainMode()
						+ "isLocal:" + isLocal()
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
	protected ArrayList<PFRDataRecord> load() {
		
		
		ArrayList<PFRDataRecord> result = new ArrayList<>();
		
		if(mapArray == null) { return result; }
		
		for(Map<String,String> map  : mapArray) {
			result.add( new PFRDataRecord(map, convertTypes) );
		}
		
		return result;
	}

}
