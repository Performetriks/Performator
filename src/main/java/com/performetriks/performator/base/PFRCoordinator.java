package com.performetriks.performator.base;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

import com.performetriks.performator.executors.PFRExec;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * This class coordinates the test execution.
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
		
		if(test != null) {
			startTestLocally(test);
		}
	}
	
	
	/*************************************************************
	 * Start the test with the given class name.
	 * 
	 * @param className 
	 *************************************************************/
	public static void startTestLocally(PFRTest test) {

		//-------------------------
		// Check Null
		if(test == null) {
			logger.info("Test could not be started as the test was null.");
			return;
		}
		
		//-------------------------
		// Get Executors
		ArrayList<PFRExec> executorList = test.getExecutors();
		
		if(executorList.size() == 0) {
			logger.info("The test "+test.getName()+" did not have any executors defined, nothing to execute.");
			return;
		}
		
		//-------------------------
		// Start HSR if not already done
		HSR.setTest(test.getName());
		HSRConfig.enable(15);
		
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
				gracefullyStopExecutorThreads(executorList);
				
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
	private static void gracefullyStopExecutorThreads(ArrayList<PFRExec> executorList) {
		
		for(PFRExec executor : executorList) {
			executor.gracefulStop();
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
				executor.execute(test.getContext());
				latch.countDown();
			}
		});
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
			
			Object instance = clazz.getDeclaredConstructor(PFRContext.class)
		    		.newInstance(new PFRContext());
			
		    return (PFRTest)instance;

		    
		} catch (Exception e) {
			logger.error("Error while creating instance for class "+className, e);
		}
		
		return null;

	}

}
