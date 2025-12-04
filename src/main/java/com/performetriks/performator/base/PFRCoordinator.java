package com.performetriks.performator.base;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.Main.CommandLineArgs;
import com.performetriks.performator.base.PFRConfig.Mode;
import com.performetriks.performator.executors.PFRExec;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.base.HSRTestSettings;

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
	
	private static ArrayList<Thread> executorThreadList = new ArrayList<>();
	
	private static ArrayList<PFRExec> executorList = null;
	
	
	/*************************************************************
	 * Start the instance in the defined mode.
	 * 
	 *************************************************************/
	public static void executeMode(Mode mode) {
		
		switch(mode) {
			case AUTO 	-> executeAuto();
			case AGENT 	-> executeAgentInstance();
			case LOCAL 	-> executeLocal();
			case REMOTE -> executeRemote();
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
		PFRTest test = getTestInstance(testClass);
		
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
		
	}
	
	/*************************************************************
	 * Start the instance and run the test locally.
	 * 
	 *************************************************************/
	public static void executeLocal() {
		
		String testClass = CommandLineArgs.pfr_test.getValue().getAsString();
		
		PFRTest test = getTestInstance(testClass);
		
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
		
	}
	
	/*************************************************************
	 * Start the instance and run a test that has been received
	 * from an agent. This mode is used by agents to run tests.
	 * 	 * 
	 *************************************************************/
	public static void executeRemote() {
		String testClass = CommandLineArgs.pfr_test.getValue().getAsString();
		
		//This also loads all the PFRConfig set in the constructor of the test.
		PFRTest test = getTestInstance(testClass);
		
		if(test != null) {
			// TODO Remove reporters
			// TODO Add reporter to report to controller
		}
	}
	
	/*************************************************************
	 * Get an instance of a PFRTest class by name.
	 * 
	 * @param className 
	 * @return instance or null on error.
	 *************************************************************/
	public static PFRTest getTestInstance(String className) {
		if(className == null) {
			logger.info("Please specify the class name of the test");
			return null;
		}
		
		try {

			Class<?> clazz = Class.forName(className);
		    
			if(! PFRTest.class.isAssignableFrom(clazz) ){
		    	logger.info("The specified test class "+className+" must be a subclass of "+PFRTest.class.getName()+".");
				return null;
		    }
			
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
		
		PFRTest test = getTestInstance(className);
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
		// Get Executors
		executorList = test.getExecutors();
		
		if(executorList.size() == 0) {
			logger.info("The test "+test.getName()+" does not have any executors defined, nothing to execute.");
			return;
		}
		
		//-------------------------
		// Set Test Name
		HSR.setTest(test.getName());
		
		//-------------------------
		// Register Settings
		registerExecutorSettings();
		
		//-------------------------
		// Execute
		HSRConfig.enable();
		startTestLocally(test);
		
		
	}
	
	
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	private static void startTestLocally(PFRTest test) {

		
		//-------------------------
		// Latch
		CountDownLatch latch = new CountDownLatch(executorList.size());
		try {
			
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
				gracefullyStopExecutorThreads();
				
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
				killExecutorThreads(executorList);
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
	 * 
	 *****************************************************************/
	private static void gracefullyStopExecutorThreads() {
		
		for(PFRExec executor : executorList) {
			executor.requestGracefulStop();
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	private static void killExecutorThreads(ArrayList<PFRExec> executorList) {
		
		//-----------------------------------
		// Terminate Executors
		for(PFRExec executor : executorList) {
			executor.terminate();
		}
		
		//-----------------------------------
		//
		for(Thread thread : executorThreadList) {
			
			try {
				if(thread.isAlive() && !thread.isInterrupted()) {
					thread.interrupt();
				}
			}catch(Throwable e) {
				HSR.addException(e);
				logger.error("Error while stopping executor thread: " + e.getMessage(), e);
			}
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	private static void terminateTest() {
		logger.info("Terminate Test Execution");
		HSRConfig.terminate();
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
