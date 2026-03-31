package com.performetriks.performator.base;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.Main.CommandLineArgs;
import com.performetriks.performator.base.PFRConfig.Mode;
import com.performetriks.performator.data.PFRDataSource;
import com.performetriks.performator.distribute.PFRAgent;
import com.performetriks.performator.distribute.PFRAgentPool;
import com.performetriks.performator.distribute.RemoteResponse;
import com.performetriks.performator.distribute.ZePFRClient;
import com.performetriks.performator.distribute.ZePFRServer;
import com.performetriks.performator.executors.PFRExec;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.base.HSRTestSettings;
import com.xresch.hsr.reporting.HSRReporterCSV;
import com.xresch.hsr.reporting.HSRReporterHTML;
import com.xresch.hsr.reporting.HSRReporterJson;
import com.xresch.hsr.reporting.HSRReporterPeekPoll;
import com.xresch.hsr.stats.HSRRecordStats;
import com.xresch.hsr.stats.HSRStatsEngine;
import com.xresch.hsr.stats.HSRStatsEngine.SummarizedStats;
import com.xresch.hsr.stats.HSRStatsEngineHooks;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * This class coordinates the test execution.
 * 
 * Limitation: It can execute a single test at once, this is due to the HSR
 * framework holding the Name of the test globally, and can't work with 
 * multiple test running in the same JVM.
 * 
 * Available arguments:
 * <ul>
 *    <li><b>test:&nbsp;</b> The class of the test to execute including package name. (e.g -Dtest=com.example.PFRTestImplementation)</li>
 * </ul>
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRCoordinator {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRCoordinator.class.getName());
	
	private static CountDownLatch latch = null;
	private static ArrayList<Thread> executorThreadList = new ArrayList<>();
	
	private static ArrayList<PFRExec> executorList = null;
	
	private static ArrayList<ZePFRClient> agentConnections = new ArrayList<>();
	
	private static ZePFRServer server = null;
	
	private static HSRReporterPeekPoll peekPoll = null;
	
	private static boolean isTestRunning = true;
	
	/*************************************************************
	 * Start the instance in the defined mode.
	 * 
	 *************************************************************/
	public static void executeMode(Mode mode) {
		
		switch(mode) {
			case AUTO 	-> executeAuto();
			case LOCAL 	-> executeLocal();
			case AGENT 	-> executeAgentInstance();
			case AGENTBORNE -> executeAgentborne();
		}
	}
	
	/*************************************************************
	 * Start the instance and run the test either locally or remote
	 * on agents if agents are defined.
	 * 
	 *************************************************************/
	public static void executeAuto() {
		
		String testClass = CommandLineArgs.pfr_test.getValue().getAsString();
		
		//This also loads all the PFRConfig set in the constructor of the test.
		PFRTest test = createTestInstance(testClass);
		
		if(test != null) {
			if(PFRConfig.hasAgents()) {
				executeOnAgents(test);
			}else {
				executeLocal(test);
			}
		}
	}

	
	/*************************************************************
	 * Start the instance as an agent.
	 * 
	 *************************************************************/
	public static void executeAgentInstance() {
		
		int agentPort = CommandLineArgs.pfr_port
										.getValue()
										.getAsInteger();
			
		server = new ZePFRServer();
	}
	
	/*************************************************************
	 * Start the instance and run the test locally.
	 * 
	 *************************************************************/
	public static void executeLocal() {
		
		String testClass = CommandLineArgs.pfr_test.getValue().getAsString();
		
		PFRTest test = createTestInstance(testClass);
		
		if(test != null) {
			executeLocal(test);
		}
	}
	
	/*************************************************************
	 * Start the instance and run the test locally.
	 * 
	 *************************************************************/
	public static void executeLocal(PFRTest test) {
		startTest(test);
	}
	
	
	/*************************************************************
	 * Start the instance and distribute the test on agents.
	 * 
	 *************************************************************/
	public static void executeOnAgents(PFRTest test) {
		
		if( ! checkCanExecute(test) ) { return; }
		
		try {
			//------------------------------
			// Reserve Agents
			agentsDisconnect();
			agentsReserve(test);
			
			//------------------------------
			// Send Jar File
			agentsTransferJar(test);
			
			//------------------------------
			// Send Jar File
			agentsRegisterStatsEngineHooks();
			
			//------------------------------
			// Start Test
			agentsStartTest(test);
			
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupt flag
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/*************************************************************
	 * Makes sure that all the agents are disconnected.
	 * 
	 *************************************************************/
	public static void agentsDisconnect() {
		
		for(ZePFRClient connection : agentConnections) {
			connection.testStop();
		}
		
		agentConnections.clear();
		
	}

	/*************************************************************
	 * Transfers the JAR file to the connected agents.
	 *************************************************************/
	private static void agentsTransferJar(PFRTest test) throws URISyntaxException, InterruptedException {
		logger.info("################################################");
		logger.info("# Transfer Test JAR File to Agents");
		logger.info("################################################");
		logger.info("Start Transfer of JAR File: " + ZePFRClient.getJarFileURIForTest(test) );
		
		CountDownLatch latch = new CountDownLatch(agentConnections.size());
		
		for(int i = 0 ; i < agentConnections.size(); i++) {
			
			agentConnections.get(i).sendJar(latch);
		}

		//------------------------------
		// Wait for Transfers to finish
		long waitTime = 1000;
		int iteration = 1;
		do {
			
			//----------------------------
			// Incremental Wait
			Thread.sleep(waitTime);
			if(iteration % 3 == 0 && waitTime < 30000) {
				waitTime *= 2;
			}
			

			//----------------------------
			// Ping agents and create Progress Log
			StringBuilder builder = new StringBuilder();
			for(int i = 0 ; i < agentConnections.size(); i++) {
				
				agentConnections.get(i).ping(); // ping agent to not lose connection during longer upload times.
				
				PFRAgent current = agentConnections.get(i).getAgent();

				builder.append(" ["+current.hostname()+": "+current.uploadProgressPercent()+"%] ");
			}
			logger.info("Upload Progress:"+builder.toString());
			
		}while(latch.getCount() > 0);
		
		logger.info("All Transfers finished");
	}
	
	/*************************************************************
	 * Adds the hooks needed to collect the statistics from the
	 * agents before aggregating the reports.
	 * 
	 *************************************************************/
	private static void agentsRegisterStatsEngineHooks() {
		
		HSRStatsEngine.setHooks( new HSRStatsEngineHooks() {
			
			@Override
			public void beforeAggregate() {
				//-------------------------------------
				// Fetch data from all agents
				TreeMap<String, ArrayList<HSRRecordStats>> groupedStats = new TreeMap<>();
				
				for(int i = 0 ; i < agentConnections.size(); i++) {
					ZePFRClient current = agentConnections.get(i);
					
					RemoteResponse response = current.statsPoll();
	
					if(response != null) { 
				
						JsonArray recordStatsArray = response.payloadAsArray();
						
						for(JsonElement e : recordStatsArray) {
							if(e.isJsonObject()) {
								HSRRecordStats stats = new HSRRecordStats(e.getAsJsonObject());
								
								String statsId = stats.statsIdentifier();
								
								if( !groupedStats.containsKey(statsId) ) {
									groupedStats.put(statsId,  new ArrayList<>());
								}
								
								groupedStats.get(statsId).add(stats);
							}
						}
						
					}
				}
				
				//-------------------------------------
				// Summarize Stats from Agents
				SummarizedStats summary = HSRStatsEngine.summarizeGroupedStats(groupedStats, true);
				
				//-------------------------------------
				// Add all to Engine
				for(HSRRecordStats stats : summary.finalRecords()) {
					HSRStatsEngine.addRecordStats(stats);
				}
				
			}
		});
	}
		
		
	/*************************************************************
	 * Reserve agents based on data defined with:
	 * <ul>
	 *     <li>PFRConfig.getAgentPool();</li>
	 *     <li>PFRConfig.getAgentAmount();</li>
	 * </ul>
	 *************************************************************/
	private static void agentsReserve(PFRTest test) {
		//------------------------------
		// Get Agents and Amount
		PFRAgentPool pool = PFRConfig.getAgentPool();
		
		int amount = PFRConfig.getAgentAmount();
		if(amount <= 0) {
			amount = pool.size();
		}
		
		//------------------------------
		// Reserve available agents
		logger.info("################################################");
		logger.info("# Check Agent Availability and Reserve. ");
		logger.info("################################################");

		for(int i = 0 ; i < amount; i++) {
			
			PFRAgent agent = pool.get(i);
			ZePFRClient connection = new ZePFRClient(agent, test);
			
			RemoteResponse status = connection.getStatus();


			//---------------------------
			// Check success
			if(status == null) {
				logger.warn(" Error connecting to agent: " + agent.hostname() + ":" + agent.port());
				continue;
			}
			
			if(!status.success()) {
				logger.warn("Error while checking agent status: " 
								+ agent.hostname() + ":" + agent.port() 
								+ ", Messages: " + PFR.JSON.toJSON(status.messages()) 
							);
				continue;
			}
			
			//---------------------------
			// Check success
			JsonObject payload = status.payload().getAsJsonObject();
			logger.info(PFR.JSON.toJSON(payload));
			
			if(payload.has(RemoteResponse.FIELD_STATUS_AVAILABLE)
			&& payload.get(RemoteResponse.FIELD_STATUS_AVAILABLE).getAsBoolean() == true) {
				RemoteResponse reserve = connection.reserveAgent(amount, i);
				if(reserve != null && reserve.success()) {
					agentConnections.add(connection);
				}
			}
			
		}
	}
	
	/*************************************************************
	 * 
	 * @param logStatus 
	 *************************************************************/
	private static boolean agentsPingIsTestRunning(boolean logStatus) {
		
		boolean isAnyTestRunning = false;
		
		StringBuilder builder = new StringBuilder();

		for(int i = 0 ; i < agentConnections.size(); i++) {
			ZePFRClient current = agentConnections.get(i);
			RemoteResponse response = current.ping();

			boolean isAgentTestRunning = response.payloadMemberAsBoolean(RemoteResponse.FIELD_STATUS_ISTESTRUNNING);
			
			isAnyTestRunning |= isAgentTestRunning;
			
			//----------------------------
			// Create Progress Log
			PFRAgent agent = current.getAgent();
			builder.append(" ["+agent.hostname()+": "+
									((isAgentTestRunning) ? "running" : "done") 
							 +"] ");
			
		}
		
		if(logStatus) {
			logger.info("Agent Test State:"+builder.toString());
		}
		
		return isAnyTestRunning;
	}
	
	/*************************************************************
	 * 
	 * @param logStatus 
	 *************************************************************/
	private static boolean agentsStopGracefully() {
		
		boolean isAnyTestRunning = false;
		
		StringBuilder builder = new StringBuilder();

		for(int i = 0 ; i < agentConnections.size(); i++) {
			ZePFRClient current = agentConnections.get(i);
			RemoteResponse response = current.testStopGracefully();

//			boolean isTestRunning = response.payloadMemberAsBoolean(RemoteResponse.FIELD_STATUS_ISTESTRUNNING);
//			
//			isAnyTestRunning |= isTestRunning;
//			
//			//----------------------------
//			// Create Progress Log
//			PFRAgent agent = current.getAgent();
//			builder.append(" ["+agent.hostname()+": "+
//									((isTestRunning) ? "running" : "done") 
//							 +"] ");
			
		}
		
//		if(logStatus) {
//			logger.info("Agent Test State:"+builder.toString());
//		}
		
		return isAnyTestRunning;
	}
	
	/*************************************************************
	 * Transfers the JAR file to the connected agents.
	 *************************************************************/
	private static void agentsStopNow(PFRTest test) {
		logger.info("################################################");
		logger.info("# Stop Agents");
		logger.info("################################################");
		logger.info("Stop remaining agents." );
		
		CountDownLatch latch = new CountDownLatch(agentConnections.size());
		
		ArrayList<ZePFRClient> agentsToStop = new ArrayList<>();
		for(int i = 0 ; i < agentConnections.size(); i++) {
			ZePFRClient current = agentConnections.get(i);
			RemoteResponse response = current.ping();

			boolean isAgentTestRunning = response.payloadMemberAsBoolean(RemoteResponse.FIELD_STATUS_ISTESTRUNNING);
			
			if(isAgentTestRunning) {
				agentsToStop.add(current);
				current.testStop(latch);
			}else {
				latch.countDown(); // nothing todo
			}
		}

		//------------------------------
		// Wait for Transfers to finish
		try {
			long waitTime = 1000;
			int iteration = 1;
			do {
				
				//----------------------------
				// Incremental Wait
				Thread.sleep(waitTime);
				if(iteration % 3 == 0 && waitTime < 30000) {
					waitTime *= 2;
				}
				
	
				//----------------------------
				// Ping agents and create Progress Log
				StringBuilder builder = new StringBuilder();
				for(int i = 0 ; i < agentsToStop.size(); i++) {
					
					ZePFRClient current = agentsToStop.get(i);
					RemoteResponse response = current.getStatus();
	
					boolean isAgentTestRunning = response.payloadMemberAsBoolean(RemoteResponse.FIELD_STATUS_ISTESTRUNNING);
	
					builder.append(" ["+current.getAgent().hostname()+": "+(isAgentTestRunning ? "stopping" : "DONE")+"] ");
				}
				
				logger.info("Stopping Progress:" + builder.toString());
				
			}while(latch.getCount() > 0);
			
			logger.info("All Agents finished");
		}catch(InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupt flag
		}
	}
		
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	private static void agentsStartTest(PFRTest test) {
		
		//-------------------------
		// Start all the Tests
		if( !prepareTestExecution(test) ) {
			return;
		}
		
		//-------------------------
		// Start all the Tests
		for(int i = 0 ; i < agentConnections.size(); i++) {
			agentConnections.get(i).testStart();
		}
		
		//-------------------------
		// 
		try {
			
			//-------------------------------
			// Wait for tests to complete
			// or max duration being reached
			long startMillis = System.currentTimeMillis();
			long endMillis = startMillis;
			long maxMillis = test.maxDuration().toMillis();
			
			while(
					agentsPingIsTestRunning(true)
				&& (endMillis - startMillis) <= maxMillis
			) {
				
				Thread.sleep(15000);
				endMillis = System.currentTimeMillis();
			}
			
			//-------------------------------
			// Terminate test
			if(agentsPingIsTestRunning(false)) {
				terminateTest();
				return;
			}
			
			//-------------------------------
			// Gracefully Stop Test
			long graceStart = System.currentTimeMillis();
			long graceEnd = graceStart;
			long graceDuration = test.gracefulStop().toMillis();
			
			if(graceDuration > 0 && agentsPingIsTestRunning(false) ){
				logger.info("Max Duration reached, initialize graceful stop of "+(graceDuration/1000)+" seconds.");
				
				agentsStopGracefully();
				
				while(
					    agentsPingIsTestRunning(true) 
					&& (graceEnd - graceStart) <= graceDuration
				) {
					Thread.sleep(15000);
					graceEnd = System.currentTimeMillis();
				}
			}
			
		}catch(Exception e) {
			logger.info("Error during Executor Thread execution.");
		}finally {
			//-------------------------------
			// Kill remaining Threads
			if( agentsPingIsTestRunning(false) ) {
				agentsStopNow(test);
			}
			
			//-------------------------------
			// Terminate Test
			terminateTest();
		}	
		
	}
	
	
	/*************************************************************
	 * Start the instance and run a test that has been received
	 * from an agent. This mode is used by agents to run tests.
	 * 	 * 
	 *************************************************************/
	public static void executeAgentborne() {
		
		//-------------------------------
		// Variables
		String testClass = CommandLineArgs.pfr_test.getValue().getAsString();
		String targetDir = CommandLineArgs.pfr_target.getValue().getAsString();
		int agentTotal = CommandLineArgs.pfr_agentTotal.getValue().getAsInteger();
		int agentIndex = CommandLineArgs.pfr_agentIndex.getValue().getAsInteger();
		
		// This also loads all the PFRConfig set in the constructor of the test.
		PFRTest test = createTestInstance(testClass);
		
		//-------------------------------
		// Change Reporters
		HSRConfig.clearReporters();
		HSRConfig.addReporter(new HSRReporterCSV(targetDir+"/report/data.csv", ";"));
		HSRConfig.addReporter(new HSRReporterJson(targetDir+"/report/data.json", true));
		HSRConfig.addReporter(new HSRReporterHTML(targetDir+"/report/HTMLReport"));
		
		peekPoll = new HSRReporterPeekPoll();
		HSRConfig.addReporter(peekPoll);
		
		//-------------------------------
		// Distribute Load
		for(PFRExec executor : test.getExecutors()) {
			executor.distributeLoad(agentTotal, agentIndex, 0);
		}
		
		//-------------------------------
		// Start Server
		server = new ZePFRServer();
		
		//-------------------------
		// Prepare and Execute
		if(test != null && prepareTestExecution(test)) {
			startTestLocally(test);
		}

	}
	
	/*************************************************************
	 * Get an instance of a PFRTest class by name.
	 * 
	 * @param className 
	 * @return instance or null on error.
	 *************************************************************/
	private static PFRTest createTestInstance(String className) {
			
		//----------------------------------
		// Check Null
		if(className == null) {
			logger.info("Please specify the class name of the test");
			return null;
		}
		
		try {

			//----------------------------------
			// Get Class
			Class<?> clazz = Class.forName(className);
		    
			if(! PFRTest.class.isAssignableFrom(clazz) ){
		    	logger.info("The specified test class "+className+" must be a subclass of "+PFRTest.class.getName()+".");
				return null;
		    }
			
			//----------------------------------
			// Reset the test execution before
			// Creating new Instance
			resetTestExecution();
			
			//----------------------------------
			// Create Instance
			Object instance = clazz.getDeclaredConstructor().newInstance();
			
		    return (PFRTest)instance;

		    
		} catch (Exception e) {
			logger.error("Error while creating instance for class "+className, e);
		}
		
		return null;

	}
	
	/*************************************************************
	 * Start the test based on argument 
	 * -Dtest=com.example.PFRTestYourImplementation.
	 * 
	 *************************************************************/
	public static void startTest() {
		
		String className = System.getProperty("test", null);
		
		if(className == null) {
			logger.info("please specify the test class using -Dtest=-Dtest=com.example.PFRTestYourImplementation.");
			return;
		}
		
		startTest(className);
	}
	
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	public static void startTest(String className) {
		
		PFRTest test = createTestInstance(className);
		startTest(test);
		
	}
	
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	public static void startTest(PFRTest test) {
		
		//-------------------------
		// Check Null
		if(test == null) {
			logger.info("Test could not be started as the test was null.");
			return;
		}
		
		//-------------------------
		// Prepare and Execute
		if(prepareTestExecution(test)) {
			startTestLocally(test);
		}
		
	}

	/*************************************************************
	 * Used to reset a test execution before starting another one.
	 * 
	 *************************************************************/
	private static void resetTestExecution() {

		PFRDataSource.clearSources();
		HSR.reset();
		HSRConfig.reset();
		
		peekPoll = null;
		
		
	}
		
	/*************************************************************
	 * Prepares a test execution, registers executors and
	 * starts the HSR framework.
	 * 
	 * @param test 
	 * @return true if successful and test can be started, false
	 * if there is nothing to execute. 
	 *************************************************************/
	private static boolean prepareTestExecution(PFRTest test) {
		
		executorList = test.getExecutors();
		
		if( !checkCanExecute(test) ) { return false; }
		
		//-------------------------
		// Set Test Name
		HSR.setTest(test.getName());
				
		//-------------------------
		// Register Settings
		registerExecutorSettings();
		
		//-------------------------
		// Execute
		HSRConfig.enable();
		
		return true;
		
	}
	
	/*************************************************************
	 * Checks if the test can be executed.
	 * 
	 * @param true if it can be executed, false otherwise.
	 *  
	 *************************************************************/
	private static boolean checkCanExecute(PFRTest test) {
		
		if(test == null) {
			logger.warn("The test was null and nothing could be executed.");
			return false;
		}
		
		if(test.getExecutors().isEmpty()) {
			logger.warn("The test "+test.getName()+" does not have any executors defined, nothing to execute.");
			return false;
		}
		
		return true;
	}
	
	
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	private static void startTestLocally(PFRTest test) {

		
		//-------------------------
		// Latch
		latch = new CountDownLatch(executorList.size());
		try {
			
			isTestRunning = true;
			//-------------------------
			// Start Executor Threads
			int i = 0;
			for(PFRExec executor : executorList) {
				
				try {
					Thread executorThread = createExecutorThread(test, executor, latch);
					
					executorThread.setName(executor.getClass().getSimpleName()+"-Thread-"+ (i++));
					executorThread.start();
					
					executorThreadList.add(executorThread);

				}catch (Exception e) {
					HSR.addException(e);
					logger.error("Error While starting Executor thread.", e);
				}
			}
			
			//-------------------------------
			// Wait for threads to complete
			// or max duration being reached
			long startMillis = System.currentTimeMillis();
			long endMillis = startMillis;
			long maxMillis = test.maxDuration().toMillis();
			
			while(
				latch.getCount() > 0 
				&& (endMillis - startMillis) <= maxMillis
			) {
				Thread.sleep(1000);
				endMillis = System.currentTimeMillis();
			}
			
			//-------------------------------
			// Terminate test
			if(latch.getCount() == 0) {
				terminateTest();
				return;
			}
			
			//-------------------------------
			// Gracefully Stop Test
			long graceStart = System.currentTimeMillis();
			long graceEnd = graceStart;
			long graceDuration = test.gracefulStop().toMillis();
			
			if(graceDuration > 0 && latch.getCount() > 0 ){
				logger.info("Max Duration reached, initialize graceful stop of "+(graceDuration/1000)+" seconds.");
				stopTestGracefully();
				
				while(
					latch.getCount() > 0 
					&& (graceEnd - graceStart) <= graceDuration
				) {
					Thread.sleep(1000);
					graceEnd = System.currentTimeMillis();
				}
			}
			
		}catch(Exception e) {
			logger.info("Error during Executor Thread execution.");
		}finally {
			//-------------------------------
			// Kill remaining Threads
			if(latch.getCount() > 0) {
				killExecutorThreads();
			}
			
			//-------------------------------
			// Terminate Test
			terminateTest();
		}	
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	private static void registerExecutorSettings() {
		
		for(PFRExec executor : executorList) {
			
			JsonObject settings = new JsonObject();
			settings.addProperty("executor", executor.getClass().getSimpleName());
			
			executor.getSettings(settings);
			
			HSRConfig.addTestSettings(
					new HSRTestSettings(
							  executor.getExecutedName()
							, settings
						)
				);
		}
		
	}
	
	/*****************************************************************
	 * Returns true if this instance has an active peekPoll reporter.
	 *****************************************************************/
	public static boolean hasPeekPoll() {
		return peekPoll != null;
	}
	
	/*****************************************************************
	 * Returns the peekPoll reporter.
	 *****************************************************************/
	public static HSRReporterPeekPoll getPeekPoll() {
		return peekPoll;
	}
	
	
	
	/*****************************************************************
	 * Requests the executors to stop gracefully.
	 *****************************************************************/
	public static void stopTestGracefully() {
		
		for(PFRExec executor : executorList) {
			executor.requestGracefulStop();
		}
	}
	
	/*****************************************************************
	 * Stops the test without gracefulness.
	 * 
	 *****************************************************************/
	public static void stopTestNow() {
		
		//---------------------------
		// Run down the latch
		while(latch.getCount() > 0) {
			latch.countDown();
		}
		
		//---------------------------
		// Kill the Threads
		killExecutorThreads();
		
		//-------------------------------
		// Terminate Test
		terminateTest();
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	private static void killExecutorThreads() {
		
		try {
			//-----------------------------------
			// Terminate Executors
			for(PFRExec executor : executorList) {
				executor.doStopNow();
				executor.terminate();
			}
			
			//-----------------------------------
			//
			for(Thread thread : executorThreadList) {
				
				
					if(thread.isAlive() && !thread.isInterrupted()) {
						thread.interrupt();
					}
				}
			}
		catch(Throwable e) {
			HSR.addException(e);
			logger.error("Error while stopping execution: " + e.getMessage(), e);
		}
	}
	
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public static boolean isTestRunning() {
		return isTestRunning;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	private static void terminateTest() {
		logger.info("Terminate Test Execution");
		HSRConfig.terminate();
		isTestRunning = false;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public static Thread createExecutorThread(PFRTest test, PFRExec executor, CountDownLatch latch) {
		
		return new Thread(new Runnable() {
			@Override
			public void run() {
				executor.execute();
				latch.countDown();
			}
		});
	}
	


}
