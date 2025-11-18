package com.performetriks.performator.data;

import java.util.ArrayList;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
public class PFRDataSourceFileCSV extends PFRDataSourceStatic {

	Logger logger = (Logger) LoggerFactory.getLogger(PFRDataSourceStatic.class.getName());
	
	private String packagePath;
	private String filename;
	private String separator = ",";
	
	/*****************************************************************
	 * Creates a new data source for a CSV file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * 
	 * @param datasourceName uniqueName for this data source.
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 *****************************************************************/
	public PFRDataSourceFileCSV(String datasourceName, String packagePath, String filename, String separator) {
		super(datasourceName);
		this.packagePath = packagePath;
		this.filename = filename;
		this.separator = separator;
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
		String csvContent = PFR.Files.readPackageResource(packagePath, filename);

		if(csvContent == null || csvContent.isBlank() ) {
			logger.warn("The file for data source \""+name()+"\" was empty or could not be read.");
			return result;
		}
		
		//----------------------------
		// Parse JSON
		ArrayList<JsonObject> potentialArray;
		try {
			potentialArray = PFR.Data.parseAsCSV(csvContent, separator);
		} catch (Exception e) {
			logger.error("The data source \""+name()+"\" had an error while parsing CSV: "+e.getMessage(), e);
			return result;
		}
		
		if(potentialArray == null ) {
			return result;
		}
		
		//----------------------------
		// Create Result
		for(JsonObject o : potentialArray) {
			result.add( new PFRDataRecord(o) );
		}
		
		return result;
	}

}
