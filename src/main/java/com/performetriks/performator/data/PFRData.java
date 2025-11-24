package com.performetriks.performator.data;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.xresch.hsr.base.HSR;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRData {
	
	
	/*******************************************************************************
	 * Enum for defining available String Parsers
	 *******************************************************************************/
	public enum CFWQueryStringParserType {

		  json("Parse the whole response as a json object or array.")
		, jsonlines("Parse each line of the response as a json object.")
//		, html("Parse the response as HTML and convert it into a flat table.")
//		, htmltables("Parse the response as HTML and extracts all table data found in the HTML.")
//		, htmltree("Parse the response as HTML and convert it into a json structure.")
//		, xml("Parse the response as XML and convert it into a flat table.")
//		, xmltree("Parse the response as XML and convert it into a json structure.")
		, csv("Parse the response as comma-separated-values(CSV).")
		, plain("Parse the response as plain text and convert it to a single record with field 'response'.")
		, http("Parse the response as HTTP and creates a single record containing either Plain HTML or if a full response is given: HTTP status, headers and body.")
		, lines("Parse the response as text and return every line as its own record.")
		;
		
		//==============================
		// Caches
		private static TreeSet<String> enumNames = null;		
		
		//==============================
		// Fields
		private String shortDescription;

		//==============================
		// Constructor
		private CFWQueryStringParserType(String shortDescription) {
			this.shortDescription = shortDescription;
		}
				
		public String shortDescription() { return this.shortDescription; }
		
		//==============================
		// Returns a set with all names
		public static TreeSet<String> getNames() {
			if(enumNames == null) {
				enumNames = new TreeSet<>();
				
				for(CFWQueryStringParserType unit : CFWQueryStringParserType.values()) {
					enumNames.add(unit.name());
				}
			}
			
			// return a clone
			return new TreeSet<String>(enumNames);
		}
				
		//==============================
		// Check if enum exists by name
		public static boolean has(String enumName) {
			return getNames().contains(enumName);
		}
		
		//==============================
		// Create a HTML List 
		public static String getDescriptionHTMLList() {
			StringBuilder builder = new StringBuilder();
			
			builder.append("<ul>");
				for(CFWQueryStringParserType type : CFWQueryStringParserType.values()) {
					builder.append("<li><b>"+type.toString()+":&nbsp;</b>"+type.shortDescription+"</li>");
				}
			builder.append("</ul>");
			return builder.toString();
		}

	}
	
	
	/****************************************************************************
	 * Creates a new data source for a JSON file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * @param datasourceName uniqueName for this data source.
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 ****************************************************************************/
	public static PFRDataSourceFileJson newSourceJson(String datasourceName, String packagePath, String filename) {
		return new PFRDataSourceFileJson(datasourceName, packagePath, filename);
	}
	
	/****************************************************************************
	 * Creates a new data source for a JSON file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * @param datasourceName uniqueName for this data source.
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 ****************************************************************************/
	public static PFRDataSourceJsonArray newSourceJsonArray(String datasourceName, JsonArray array) {
		return new PFRDataSourceJsonArray(datasourceName, array);
	}
	
	/****************************************************************************
	 * Creates a new data source for a CSV file.
	 * The file has to contain a JsonArray of JsonObjects.
	 * If this is not the case, the data source will be empty on
	 * load.
	 * @param datasourceName uniqueName for this data source.
	 * @param packagePath the path of the package that contains the
	 * testdata file
	 * @param filename the name of the file
	 ****************************************************************************/
	public static PFRDataSourceFileCSV newSourceCSV(String datasourceName, String packagePath, String filename, String separator) {
		return new PFRDataSourceFileCSV(datasourceName, packagePath, filename, separator);
	}
	
//	/****************************************************************************
//	 * Parse the string with the given Type
//	 ****************************************************************************/
//	public static ArrayList<JsonObject> parse(String type, CFWHttpResponse response) throws Exception {
//		
//		type = type.trim().toLowerCase();
//		
//		if( !CFWQueryStringParserType.has(type) ){
//			HSR.addErrorMessage(" Parser Type: "+type+"' is not known."
//						+" Available options: "+HSR.JSON.toJSON( CFWQueryStringParserType.getNames()) );
//			return new ArrayList<>();
//		}
//		
//		return parse( type, response);
//	}
//	/****************************************************************************
//	 * Parse the string with the given Type
//	 * @param csvSeparator TODO
//	 ****************************************************************************/
//	public static ArrayList<JsonObject> parse(CFWQueryStringParserType type, CFWHttpResponse response, String csvSeparator) throws Exception{
//		
//		if(response == null) { return new ArrayList<>();}
//		
//		if(type == CFWQueryStringParserType.http) {
//			return parseAsHTTP(response);
//		}else {
//			return parse(type, response.getResponseBody(), csvSeparator);
//		}
//	}
	
	/****************************************************************************
	 * Parse the string with the given Type
	 * @param csvSeparator TODO
	 ****************************************************************************/
	public static ArrayList<JsonObject> parse(String type, String data, String csvSeparator) throws Exception {
		
		type = type.trim().toLowerCase();
		
		if( !CFWQueryStringParserType.has(type) ){
			HSR.addErrorMessage(" Parser Type: "+type+"' is not known."
						+" Available options: "+HSR.JSON.toJSON( CFWQueryStringParserType.getNames()) );
			return new ArrayList<>();
		}
		
		return parse( CFWQueryStringParserType.valueOf(type), data, csvSeparator);
		
	}
	
	/******************************************************************
	 * Parses the string with the 
	 * @param csvSeparator TODO
	 ******************************************************************/
	public static ArrayList<JsonObject> parse(CFWQueryStringParserType type, String data, String csvSeparator) throws Exception {
		ArrayList<JsonObject> result = new ArrayList<>();

		switch(type) {
			
			case json:			result = parseAsJson(data);	break;	
			case jsonlines:		result = parseAsJsonLines(data);	break;	
//			case html:			result = parseAsHTML(data); 	break;
//			case htmltables:	result = parseAsHTMLTables(data); 	break;
//			case htmltree:		result = parseAsHTMLTree(data); 	break;
//			case xml:			result = parseAsXML(data); 	break;
//			case xmltree:		result = parseAsXMLTree(data); 	break;
			case csv:			result = parseAsCSV(data, csvSeparator); 	break;
			case plain:			result = parseAsPlain(data); 	break;
			case lines:			result = parseAsLines(data); 	break;
			
			// Fall back to HTML
			case http:			result = parseAsPlain(data); break;

		}


		return result;
	}
		
	
	/******************************************************************
	 *
	 ******************************************************************/
	public static ArrayList<JsonObject> parseAsJsonLines( String data ) throws Exception {
		
		ArrayList<JsonObject> result = new ArrayList<>();
		 
		Scanner scanner = new Scanner(data.trim());

		while(scanner.hasNextLine()) {
			
			String line = scanner.nextLine();
			result.addAll(parseAsJson(line));
		}
		
		return result;
	}
	
	/******************************************************************
	 *
	 ******************************************************************/
	public static ArrayList<JsonObject> parseAsJson( String data ) throws Exception {
		
		ArrayList<JsonObject> result = new ArrayList<>();
		
		//------------------------------------
		// Parse Data
		JsonElement element;
		
		element = PFR.JSON.fromJson(data);
		
		//------------------------------------
		// Handle Object
		if(element.isJsonObject()) {
			
			result.add( element.getAsJsonObject() );
			return result;
		}
		
		//------------------------------------
		// Handle Array
		if(element.isJsonArray()) {
			
			for(JsonElement current : element.getAsJsonArray() ) {
				if(current.isJsonObject()) {
					result.add( current.getAsJsonObject());
				}
			}
			
		}
		
		return result;
	}
	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsXML(String data) throws Exception {
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		 
//		//------------------------------------
//		// Parse Data
//		
//		Document document = CFW.XML.parseToDocument(data);
//		JsonArray array = CFW.XML.convertDocumentToJson(document, "", true);
//		
//		for(JsonElement element : array) {
//			JsonObject object = new JsonObject(element.getAsJsonObject());
//			result.add( object );
//		}
//			
//		return result;
//		
//	}
	
	/******************************************************************
	 *
	 ******************************************************************/
	public static ArrayList<JsonObject> parseAsCSV(String data, String separator) throws Exception {
		
		ArrayList<JsonObject> result = new ArrayList<>();
		
		//------------------------------------
		// Parse Data
		JsonArray array = PFR.CSV.toJsonArray(data, separator, false, true);
		
		for(JsonElement element : array) {
			JsonObject object = element.getAsJsonObject();
			result.add( object );
		}
		
		return result;
		
	}
	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsXMLTree(String data) throws Exception {
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		 
//		//------------------------------------
//		// Parse Data
//		Document document = CFW.XML.parseToDocument(data);
//		JsonArray array = CFW.XML.convertDocumentToJson(document, "", false);
//		
//		for(JsonElement element : array) {
//			JsonObject object = new JsonObject(element.getAsJsonObject());
//			result.add( object );
//		}
//			
//		return result;
//		
//	}
//	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsHTML(String data) throws Exception {
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		 		
//		//------------------------------------
//		// Parse Data
//		org.jsoup.nodes.Document document = CFW.HTML.parseToDocument(data);
//		JsonArray array = CFW.HTML.convertDocumentToJson(document, "", true);
//		
//		for(JsonElement element : array) {
//			JsonObject object = new JsonObject(element.getAsJsonObject());
//			result.add( object );
//		}
//		
//		return result;
//		
//	}
	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsHTMLTree(String data) throws Exception{
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		 
//		//------------------------------------
//		// Parse Data
//
//		org.jsoup.nodes.Document document = CFW.HTML.parseToDocument(data);
//		JsonArray array = CFW.HTML.convertDocumentToJson(document, "", false);
//		
//		for(JsonElement element : array) {
//			JsonObject object = new JsonObject(element.getAsJsonObject());
//			result.add( object );
//		}
//			
//		return result;
//		
//	}
	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsHTMLTables(String data) throws Exception{
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		 
//		//------------------------------------
//		// Parse Data
//		org.jsoup.nodes.Document document = CFW.HTML.parseToDocument(data);
//		JsonArray array = CFW.HTML.extractTablesAsJsonArray(document);
//		
//		for(JsonElement element : array) {
//			JsonObject object = new JsonObject(element.getAsJsonObject());
//			result.add( object );
//		}
//			
//		return result;
//		
//	}
	
	/******************************************************************
	 *
	 ******************************************************************/
	public static ArrayList<JsonObject> parseAsPlain(String data) throws Exception {
		
		ArrayList<JsonObject> result = new ArrayList<>();
		 
		//------------------------------------
		// Parse Data	
		JsonObject object = new JsonObject();
		object.addProperty("result", data);
		result.add( object );
			
		return result;
		
	}
	
	/******************************************************************
	 *
	 ******************************************************************/
	public static ArrayList<JsonObject> parseAsLines(String data) throws Exception {
		
		ArrayList<JsonObject> result = new ArrayList<>();
		 
		//------------------------------------
		// Parse Data

		if(data != null && !data.isBlank()) {
			
			for( String line : data.split("\n\r|\n") ){
				JsonObject object = new JsonObject();
				object.addProperty("line", line);
				result.add( object );
			}
		}
			
		return result;
		
	}
	
//	/******************************************************************
//	 *
//	 ******************************************************************/
//	public static ArrayList<JsonObject> parseAsHTTP(CFWHttpResponse response) throws Exception {
//		
//		ArrayList<JsonObject> result = new ArrayList<>();
//		
//		//------------------------------------
//		// Parse Data
//		JsonObject object = new JsonObject();
//		object.addProperty("url", response.getURL().toString());
//		object.addProperty("status", response.getStatus());
//		object.addProperty("duration", response.getDuration());
//		object.add("headers", response.getHeadersAsJson());
//		object.addProperty("body", response.getResponseBody());
//		result.add( object );
//			
//		return result;
//		
//	}
	
	
	
	
}