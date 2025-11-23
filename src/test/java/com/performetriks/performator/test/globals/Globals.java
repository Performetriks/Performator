package com.performetriks.performator.test.globals;

import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.reporting.HSRReporterCSV;
import com.xresch.hsr.reporting.HSRReporterDatabasePostGres;
import com.xresch.hsr.reporting.HSRReporterHTML;
import com.xresch.hsr.reporting.HSRReporterJson;
import com.xresch.hsr.reporting.HSRReporterSysoutCSV;

import ch.qos.logback.classic.Level;

public class Globals {
	
	public static final String DIR_RESULTS = "./target";
	public static final int REPORT_INTERVAL_SECONDS = 5;
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public static void commonInitialization() {
		//--------------------------
		// Log Levels
		HSRConfig.setLogLevelRoot(Level.WARN);
		HSRConfig.setLogLevel(Level.INFO, "com.performetriks.performator");
		HSRConfig.setLogLevel(Level.INFO, "com.xresch.hsr");
		
		//--------------------------
		// Set Test Properties
		HSRConfig.addProperty("[Custom] Environment", "TEST");
		HSRConfig.addProperty("[Custom] Testdata Rows", "120");
		
		//--------------------------
		// Optional: Disabling System Usage Stats
//		HSRConfig.statsProcessMemory(false);
//		HSRConfig.statsHostMemory(false);
//		HSRConfig.statsCPU(false);
//		HSRConfig.statsDiskUsage(false);
//		HSRConfig.statsDiskIO();
//		HSRConfig.statsNetworkIO();
		
		//--------------------------
		// Optional: Log every datapoint
		// potential performance impact, debug use only
//		HSRConfig.setRawDataLogPath(DIR_RESULTS+"/raw.log");
		
		//--------------------------
		// Define Sysout Reporters
		HSRConfig.addReporter(new HSRReporterSysoutCSV(" | "));
		//HSRConfig.addReporter(new HSRReporterSysoutJson());
		
		//--------------------------
		// Define File Reporters
		HSRConfig.addReporter(new HSRReporterJson( DIR_RESULTS + "/hsr-stats.json", true) );
		HSRConfig.addReporter(new HSRReporterCSV( DIR_RESULTS + "/hsr-stats.csv", ",") );
		HSRConfig.addReporter(new HSRReporterHTML( DIR_RESULTS + "/HTMLReport") );
		
		//--------------------------
		// Database Reporters
		HSRConfig.addReporter(
			new HSRReporterDatabasePostGres(
				"localhost"
				, 5432
				, "postgres"	// dbname
				, "hsr"			// table name prefix
				, "postgres"	// user
				, "postgres"	// pw
			)
		);
		
    	//------------------------------
    	// EMP Reporter
//		HSRConfig.addReporter(
//    			new HSRReporterEMP(
//    					"http://localhost:8888"
//    					,"gatlytron-test-token-MSGIUzrLyUsOypYOkekVgmlfjMpLbRCA"
//    				)
//    			);
    	
    	//------------------------------
    	// JDBC DB Reporter
//		HSRConfig.addReporter(
//    			new HSRReporterDatabaseJDBC("org.h2.Driver"
//    					, "jdbc:h2:tcp://localhost:8889/./datastore/h2database;MODE=MYSQL;IGNORECASE=TRUE"
//    					, "hsr"
//    					, "sa"
//    					, "sa") {
//					
//					@Override
//					public HSRDBInterface getGatlytronDB(DBInterface dbInterface, String tableNamePrefix) {
//						return new HSRDBInterface(dbInterface, tableNamePrefix);
//					}
//
//					@Override
//					public void reportSummary(ArrayList<HSRRecordStats> summaryRecords,
//							JsonArray summaryRecordsWithSeries, TreeMap<String, String> properties,
//							JsonObject slaForRecords) {
//						// TODO Auto-generated method stub
//						
//					}
//				}
//    		);
		
		//--------------------------
		// Enable
		HSRConfig.setInterval(REPORT_INTERVAL_SECONDS); 
		HSRConfig.enable(); 
		
	}
}
