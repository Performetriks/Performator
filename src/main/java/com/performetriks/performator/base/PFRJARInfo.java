package com.performetriks.performator.base;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.data.PFRDataSource;
import com.performetriks.performator.executors.PFRExec;
import com.xresch.xrutils.annotation.XRAnnotations;

public class PFRJARInfo {

	private static final String FIELDNAME_DATASOURCES = "datasources";
	private static final String FIELDNAME_TESTS = "tests";
	private static final String FIELDNAME_PROPERTIES = "systemProperties";
	private static final String FIELDNAME_ENVIRONMENT = "environmentVars";
	
	private JsonObject infoObject = new JsonObject();
	
	
	/**************************************************************************************
	 * Wraps the given JsonObject.
	 **************************************************************************************/
	public PFRJARInfo(JsonObject infoObject) {
		this.infoObject = infoObject;
	}
	
	/**************************************************************************************
	 * Reads the information of the tests marked with @XRDiscoverable and other information.
	 **************************************************************************************/
	public PFRJARInfo() {
		
		//---------------------------
		// Test Classes
		ArrayList<Class<PFRTest>> testClasses = XRAnnotations.discover(PFRTest.class);
		
		JsonArray testArray = new JsonArray();
		
		for(Class<PFRTest> clazz : testClasses) {

			PFRJARInfoTest testObject = new PFRJARInfoTest(clazz);
			//-------------------------
			// Add to array
			testArray.add(testObject.toJson());	
		}
		
		//---------------------------
		// Environment Info
		JsonObject environment = new JsonObject(); 
		
		for(Entry<String, String> entry : System.getenv().entrySet()) {
			environment.addProperty(entry.getKey(), entry.getValue());
		}
		
		//---------------------------
		// Environment Info
		JsonObject properties = new JsonObject(); 
		
		for(Entry<Object, Object> entry : System.getProperties().entrySet()) {
			properties.addProperty(entry.getKey().toString(), entry.getValue().toString());
		}
		
		//---------------------------
		// Create Info Object
		infoObject.add(FIELDNAME_TESTS, testArray);
		infoObject.add(FIELDNAME_DATASOURCES, PFRDataSource.getRegisteredSourcesInfo());
		infoObject.add(FIELDNAME_PROPERTIES, properties);
		infoObject.add(FIELDNAME_ENVIRONMENT, environment);
		
	}
	
	/**************************************************************************************
	 * @return JsonArray with datasources, or empty object if not found.
	 **************************************************************************************/
	public ArrayList<PFRJARInfoTest> getTests() {
		
		ArrayList<PFRJARInfoTest> result = new ArrayList<>();
		
		for(JsonElement element : infoObject.get(FIELDNAME_TESTS).getAsJsonArray()) {
			
			result.add( 
					new PFRJARInfoTest(element.getAsJsonObject()) 
				);	
		}

		return result;
	}
	
	/**************************************************************************************
	 * @return JsonObject with environment variables, or empty object if not found.
	 **************************************************************************************/
	public JsonObject getEnvVariables() {
		if(infoObject.has(FIELDNAME_ENVIRONMENT)) {
			return infoObject.get(FIELDNAME_ENVIRONMENT).getAsJsonObject();
		}
		
		return new JsonObject();
	}
	
	/**************************************************************************************
	 * @return JsonObject with environment variables, or empty object if not found.
	 **************************************************************************************/
	public JsonObject getProperties() {
		if(infoObject.has(FIELDNAME_PROPERTIES)) {
			return infoObject.get(FIELDNAME_PROPERTIES).getAsJsonObject();
		}
		
		return new JsonObject();
	}
	
	/**************************************************************************************
	 * Returns the JsonObject held by this instance.
	 **************************************************************************************/
	public JsonObject toJson() {
		return infoObject;
	}


	/**************************************************************************************
	 * Returns the JsonObject held by this instance.
	 **************************************************************************************/
	public class PFRJARInfoTest {
		
		private static final String FIELDNAME_EXECUTORS = "executors";
		private static final String FIELDNAME_MAX_DURATION = "maxDurationMillis";
		private static final String FIELDNAME_GRACEFUL_STOP = "gracefulStopMillis";
		private static final String FIELDNAME_NAME = "name";
		private static final String FIELDNAME_CLASS = "class";
		
		JsonObject testObject = new JsonObject();
		
		/**************************************************************************************
		 * Wraps the given JsonObject.
		 **************************************************************************************/
		public PFRJARInfoTest(JsonObject infoObject) {
			this.testObject = infoObject;
		}
		
		/**************************************************************************************
		 * Wraps the given JsonObject.
		 **************************************************************************************/
		public PFRJARInfoTest(Class<PFRTest> clazz) {
			
			//-------------------------
			// Classname
			String classname = clazz.getName().replace("/", ".");
			testObject.addProperty(FIELDNAME_CLASS, classname);
			
			//-------------------------
			// Other Info
			PFRTest instance = PFRTest.createTestInstance(classname);
			
			if(instance == null) { return; }
			testObject.addProperty(FIELDNAME_NAME, instance.getName());
			testObject.addProperty(FIELDNAME_GRACEFUL_STOP, instance.gracefulStop().toMillis());
			Duration max = instance.maxDuration();
			testObject.addProperty(FIELDNAME_MAX_DURATION, (max == null) ? null : max.toMillis());
			
			//-------------------------
			// Executors
			JsonArray executorArray = new JsonArray();
			for(PFRExec exec : instance.getExecutors()) {
				
				PFRJARInfoExecutor executorObject = new PFRJARInfoExecutor(exec);
				executorArray.add(executorObject.toJson());
			}
			testObject.add(FIELDNAME_EXECUTORS, executorArray);
		}
		

		/**************************************************************************************
		 * @return name of the test class, or null if not found.
		 **************************************************************************************/
		public String getTestClass() {
			if(testObject.has(FIELDNAME_CLASS)) {
				return testObject.get(FIELDNAME_CLASS).getAsString();
			}
			
			return null;
			
		}
		
		/**************************************************************************************
		 * @return graceful stop time, or null if undefined.
		 **************************************************************************************/
		public Long getGracefulStopDuration() {
			if(testObject.has(FIELDNAME_GRACEFUL_STOP)) {
				return testObject.get(FIELDNAME_GRACEFUL_STOP).getAsLong();
			}
			
			return null;
		}
		
		/**************************************************************************************
		 * @return graceful stop time, or null if undefined.
		 **************************************************************************************/
		public Long getMaxDuration() {
			if(testObject.has(FIELDNAME_MAX_DURATION)) {
				return testObject.get(FIELDNAME_MAX_DURATION).getAsLong();
			}
			
			return null;
		}
		
		/**************************************************************************************
		 * @return JsonArray with datasources, or empty object if not found.
		 **************************************************************************************/
		public ArrayList<PFRJARInfoExecutor> getExecutors() {
			
			ArrayList<PFRJARInfoExecutor> result = new ArrayList<>();
			
			for(JsonElement element : infoObject.get(FIELDNAME_EXECUTORS).getAsJsonArray()) {
				
				result.add( 
						new PFRJARInfoExecutor(element.getAsJsonObject()) 
					);	
			}

			return result;
		}
		
		
		
		/**************************************************************************************
		 * Returns the JsonObject held by this instance.
		 **************************************************************************************/
		public JsonObject toJson() {
			return testObject;
		}
	}
	
	
	/**************************************************************************************
	 * Returns the JsonObject held by this instance.
	 **************************************************************************************/
	public class PFRJARInfoExecutor {
		
		private static final String FIELDNAME_SETTINGS = "settings";
		private static final String FIELDNAME_EXECUTED_NAME = "executedName";
		private static final String FIELDNAME_MAX_DURATION = "maxDurationMillis";
		private static final String FIELDNAME_GRACEFUL_STOP = "gracefulStopMillis";
		private static final String FIELDNAME_CLASS = "class";
		JsonObject executorObject = new JsonObject();
		
		/**************************************************************************************
		 * Wraps the given JsonObject.
		 **************************************************************************************/
		public PFRJARInfoExecutor(JsonObject executorObject) {
			this.executorObject = executorObject;
		}
		
		/**************************************************************************************
		 * Wraps the given JsonObject.
		 **************************************************************************************/
		public PFRJARInfoExecutor(PFRExec exec) {
			
			JsonObject executor = new JsonObject(); 
			executor.addProperty(FIELDNAME_CLASS, exec.getClass().getName().replace("/", ".") );
			executor.addProperty(FIELDNAME_EXECUTED_NAME, exec.getExecutedName() );
			executor.addProperty(FIELDNAME_GRACEFUL_STOP, exec.gracefulStop().toMillis());
			
			Duration execMax = exec.maxDuration();
			executor.addProperty(FIELDNAME_MAX_DURATION, (execMax == null) ? null : execMax.toMillis());
			
			JsonObject executorSettings = new JsonObject(); 
			exec.getSettings(executorSettings);
			executor.add(FIELDNAME_SETTINGS, executorSettings);

		}
		
		/**************************************************************************************
		 * @return name of the test class, or null if not found.
		 **************************************************************************************/
		public String getExecutorClass() {
			if(executorObject.has(FIELDNAME_CLASS)) {
				return executorObject.get(FIELDNAME_CLASS).getAsString();
			}
			
			return null;
			
		}
		
		/**************************************************************************************
		 * @return name of the executed PFRTest, or null if not found.
		 **************************************************************************************/
		public String getExecutedName() {
			if(executorObject.has(FIELDNAME_CLASS)) {
				return executorObject.get(FIELDNAME_CLASS).getAsString();
			}
			
			return null;
			
		}
		
		/**************************************************************************************
		 * @return graceful stop time, or null if undefined.
		 **************************************************************************************/
		public Long getGracefulStopDuration() {
			if(executorObject.has(FIELDNAME_GRACEFUL_STOP)) {
				return executorObject.get(FIELDNAME_GRACEFUL_STOP).getAsLong();
			}
			
			return null;
		}
		
		/**************************************************************************************
		 * @return graceful stop time, or null if undefined.
		 **************************************************************************************/
		public Long getMaxDuration() {
			if(executorObject.has(FIELDNAME_MAX_DURATION)) {
				return executorObject.get(FIELDNAME_MAX_DURATION).getAsLong();
			}
			
			return null;
		}
		
		/**************************************************************************************
		 * @return graceful stop time, or null if undefined.
		 **************************************************************************************/
		public JsonObject getSettings() {
			if(executorObject.has(FIELDNAME_SETTINGS)) {
				return executorObject.get(FIELDNAME_SETTINGS).getAsJsonObject();
			}
			
			return null;
		}

		/**************************************************************************************
		 * Returns the JsonObject held by this instance.
		 **************************************************************************************/
		public JsonObject toJson() {
			return executorObject;
		}
	}
}
