package com.performetriks.performator.test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.reporting.HSRReporterCSV;
import com.xresch.hsr.reporting.HSRReporterDatabasePostGres;
import com.xresch.hsr.reporting.HSRReporterHTML;
import com.xresch.hsr.reporting.HSRReporterJson;
import com.xresch.hsr.reporting.HSRReporterSysoutCSV;
import com.xresch.hsr.stats.HSRExpression.Operator;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;
import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;
import com.xresch.hsr.stats.HSRSLA;

import ch.qos.logback.classic.Level;

public class TestExampleLoadTestEmulation {

	public static final String DIR_RESULTS = "./target";
	public static final int REPORT_INTERVAL_SECONDS = 1;
	
	private static final HSRSLA SLA_P90_LTE_100MS = new HSRSLA(HSRMetric.p90, Operator.LTE, 100); 
	
	private static final HSRSLA SLA_P90_AND_AVG = new HSRSLA(HSRMetric.p90, Operator.LTE, 100)
															.and(HSRMetric.avg, Operator.LTE, 50); 
	
	private static final HSRSLA SLA_AVG_OR_P90 = new HSRSLA(HSRMetric.avg, Operator.LTE, 50)
														.or(HSRMetric.p90, Operator.LTE, 100); 
	
	private static final HSRSLA SLA_FAILRATE_LT_10 = new HSRSLA(HSRMetric.failrate, Operator.LT, 10); 
	
	/************************************************************************
	 * 
	 ************************************************************************/
	@BeforeAll
	static void config() {
		//--------------------------
		// Log Levels
		HSRConfig.setLogLevelRoot(Level.WARN);
		HSRConfig.setLogLevel(Level.WARN, "com.xresch.hsr.reporting");
		
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
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Test
	void emulateLoadTest() throws InterruptedException {
		
		int multiplier = 3;
		int users = 10 * multiplier;
		int rampUpMillis = 200;
		int executionsPerUser = 5 * multiplier;
		
		CountDownLatch latch = new CountDownLatch(users);
		
		for(int i = 0; i < users; i ++) {
			Thread userThread = createUserThread(executionsPerUser, latch);
			
			userThread.setName("User-"+i);
			userThread.start();
			HSR.increaseUsers(1);
			
			Thread.sleep(rampUpMillis);
		}
		
		latch.await();
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public Thread createUserThread(int executionsPerUser, CountDownLatch latch) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				
				
				try {
					for(int k = 0; k < executionsPerUser; k++) {
						//-------------------------------
						// 
						HSR.start("000_Open_Homepage");
							Thread.sleep(HSR.Random.integer(50, 200));
						HSR.end();
						
						//-------------------------------
						// 
						HSR.start("010_Login");
							Thread.sleep(HSR.Random.integer(100, 300));
						HSR.end();
						
						HSR.startGroup("015_MyGroup");
							HSR.startGroup("017_MySubGroup");
								//-------------------------------
								// 
								HSR.start("020_Execute_Search");
									Thread.sleep(HSR.Random.integer(100, 5000));
								HSR.end();
								
								//-------------------------------
								// 
								HSR.start("030_Click_Result");
									Thread.sleep(HSR.Random.integer(100, 200));
								HSR.end();
								
								//-------------------------------
								// 
								HSR.start("040_SometimesFails");
									Thread.sleep(HSR.Random.integer(50, 100));
									
									boolean isSuccess = HSR.Random.bool();
									if(!isSuccess) {
										HSR.addErrorMessage("Exception Occured: Figure it out!");
										HSR.addException(new Exception("This is an exception."));
									}
								HSR.end(isSuccess);
								
								//-------------------------------
								// 
								HSR.start("050_RandomStatusAndCode");
									Thread.sleep(HSR.Random.integer(10, 200));
									String code = HSR.Random.fromArray(new String[] {"200", "200", "200", "200", "200", "401", "500"});
								HSR.end(HSR.Random.fromArray(HSRRecordStatus.values()), code);
								
								//-------------------------------
								// 
								HSR.assertEquals("A"
										, HSR.Random.fromArray(new String[] {"A", "A", "A", "B"})
										,  "060_Assert_ContainsA");

							HSR.end();
						HSR.end();
						
						//-------------------------------
						// 
						HSR.start("070_CustomValues");
							Thread.sleep(HSR.Random.integer(100, 300));
							
							// Add a Gauge, will be averaged in aggregation
							HSR.addGauge("070.1 Gauge: SessionCount", HSR.Random.bigDecimal(80, 250));
							
							// Add a Count, will be summed up in aggregation 
							HSR.addCount("070.2 Count: TiramisusEaten", HSR.Random.bigDecimal(0, 100));
							HSR.addInfoMessage(HSR.Random.from("Valeria", "Roberta", "Ariella") + " has eaten the Tiramisu!");
							
							// Add a Metric, will calculate statistical values for it
							HSR.addMetric("070.3 Metric: TimeWalked", HSR.Random.bigDecimal(100, 300));
							
							// Add a Ranged Metric
							// simulate a correlation between count and duration
							int multiplier = HSR.Random.integer(0, 10);
							int count = multiplier * HSR.Random.integer(1, 900);
							int duration = multiplier * HSR.Random.integer(10, 1000);
							HSR.addMetricRanged("070.4 TableLoadTime", new BigDecimal(duration), count, 50);
							
						HSR.end(HSR.Random.fromArray(HSRRecordStatus.values()));
						
						//-------------------------------
						// 
						HSR.startGroup("075 ServiceLevelAgreements");
							
							HSR.start("080_SLA_P90-NOK", SLA_P90_LTE_100MS);
								Thread.sleep(HSR.Random.integer(80, 120));
							HSR.end();
							
							HSR.start("085_SLA_P90-OK", SLA_P90_LTE_100MS);
								Thread.sleep(HSR.Random.integer(50, 100));
							HSR.end();
							
							HSR.start("090_SLA_P90_AND_AVG-NOK-P90", SLA_P90_AND_AVG);
								Thread.sleep(HSR.Random.fromInts(10,10,10, 10, 10, 101));
							HSR.end();
							
							HSR.start("100_SLA_P90_AND_AVG-NOK-AVG", SLA_P90_AND_AVG);
								Thread.sleep(HSR.Random.fromInts(50,50,50, 50, 50, 90));
							HSR.end();
							
							HSR.start("110_SLA_P90_AND_AVG-OK", SLA_P90_AND_AVG);
								Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 90));
							HSR.end();
							
							HSR.start("120_SLA_AVG_OR_P90-OK-ByAvg", SLA_AVG_OR_P90);
								Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 110));
							HSR.end();
							
							HSR.start("130_SLA_AVG_OR_P90-OK-ByP90", SLA_AVG_OR_P90);
								Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 90));
							HSR.end();
							
							HSR.start("140_SLA_AVG_OR_P90-NOK", SLA_AVG_OR_P90);
								Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
							HSR.end();
							
							HSR.start("150_SLA_FAILS_LT_10-OK", SLA_FAILRATE_LT_10);
								Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
							HSR.end( (HSR.Random.integer(0, 100) > 5) ? true : false );
							
							HSR.start("160_SLA_FAILS_LT_10-NOK", SLA_FAILRATE_LT_10);
								Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
							HSR.end( (HSR.Random.integer(0, 100) > 20) ? true : false );
						
						HSR.end( ); // Group SLA
						
						//-------------------------------
						// Keep it open to test HSR. endAllOpen()
						HSR.start("999 The Unending Item");
							Thread.sleep(HSR.Random.integer(15, 115));
							
						//-------------------------------
						// Make sure everything's closed
						HSR.endAllOpen(HSRRecordStatus.Aborted);
						
					}
				}catch(InterruptedException e) {
					System.out.println("Ooops!");
					
				}finally {
					latch.countDown();
					HSR.decreaseUsers(1);
				}
			}
		});
	}
}