package com.performetriks.performator.data;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.xresch.xrutils.data.XRRecord;


/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRDataSourceFileCSV extends PFRDataSourceStatic {

	Logger logger =  LoggerFactory.getLogger(PFRDataSourceStatic.class.getName());
	
	private String packagePath;
	private String filename;
	private String separator = ",";
	
	/*****************************************************************
	 * Creates a new data source for a CSV file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * 
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 * @param separator the separator used in the CSV file
	 * 
	 *****************************************************************/
	public PFRDataSourceFileCSV(String packagePath, String filename, String separator) {
		this(null, packagePath, filename, separator);
	}
	
	/*****************************************************************
	 * Creates a new data source for a CSV file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * 
	 * @param uniqueName uniqueName for this data source.
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 * @param separator the separator used in the CSV file
	 *****************************************************************/
	public PFRDataSourceFileCSV(String uniqueName, String packagePath, String filename, String separator) {
		super(uniqueName);
		this.packagePath = packagePath;
		this.filename = filename;
		this.separator = separator;
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
			uniqueName =  packagePath 
						+ " | " + filename
						+ " | " + separator
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
			result.add( new XRRecord(o) );
		}
		
		return result;
	}

}
