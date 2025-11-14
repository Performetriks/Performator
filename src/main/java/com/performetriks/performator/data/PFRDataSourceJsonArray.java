package com.performetriks.performator.data;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class PFRDataSourceJsonArray extends PFRDataSource {

	JsonArray array;
	
	public PFRDataSourceJsonArray(String datasourceName, JsonArray array) {
		super(datasourceName);
		this.array = array;
	}

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
