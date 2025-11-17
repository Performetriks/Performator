package com.performetriks.performator.data;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;


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
	 * Constructor
	 *****************************************************************/
	public PFRDataSourceJsonArray(String datasourceName, JsonArray array) {
		super(datasourceName);
		this.array = array;
	}

	/*****************************************************************
	 * Loads the data.
	 * 
	 * @return list or records.
	 *****************************************************************/
	@Override
	protected ArrayList<PFRDataRecord> load() {
		
		ArrayList<PFRDataRecord> result = new ArrayList<>();
		
		for(JsonElement e : array) {
			if(e.isJsonObject()) {
				result.add( new PFRDataRecord(e.getAsJsonObject()) );
			}
		}
		
		return result;
	}

}
