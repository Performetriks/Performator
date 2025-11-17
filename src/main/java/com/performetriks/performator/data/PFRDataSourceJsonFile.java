package com.performetriks.performator.data;

import java.util.ArrayList;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.performetriks.performator.base.PFR;

import ch.qos.logback.classic.Logger;


/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRDataSourceJsonFile extends PFRDataSourceStatic {

	Logger logger = (Logger) LoggerFactory.getLogger(PFRDataSourceStatic.class.getName());
	
	private String packagePath;
	private String filename;
	
	/*****************************************************************
	 * Creates a new data source for a JSON file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * 
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 *****************************************************************/
	public PFRDataSourceJsonFile(String datasourceName, String packagePath, String filename) {
		super(datasourceName);
		this.packagePath = packagePath;
		this.filename = filename;
	}

	/*****************************************************************
	 * Loads the data.
	 * 
	 * @return list or records.
	 *****************************************************************/
	@Override
	protected ArrayList<PFRDataRecord> load() {
		
		ArrayList<PFRDataRecord> result = new ArrayList<>();
		
		//----------------------------
		// Load the file
		String jsonContent = PFR.Files.readPackageResource(packagePath, filename);

		if(jsonContent == null || jsonContent.isBlank() ) {
			logger.warn("The file for data source \""+name()+"\" was empty or could not be read.");
			return result;
		}
		
		//----------------------------
		// Parse JSON
		JsonElement potentialArray = PFR.JSON.fromJson(jsonContent);
		
		if(!potentialArray.isJsonArray()) {
			logger.warn("The data source \""+name()+"\" did not contain a JSON Array.");
			return result;
		}
		
		JsonArray array = potentialArray.getAsJsonArray();
		
		//----------------------------
		// Create Result

		for(JsonElement e : array) {
			if(e.isJsonObject()) {
				result.add( new PFRDataRecord(e.getAsJsonObject()) );
			}
		}
		
		return result;
	}

}
