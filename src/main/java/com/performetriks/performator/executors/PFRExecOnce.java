package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * Executes a use case exactly once with one user.
 * 
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRExecOnce extends PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecOnce.class.getName());
	
	private long offsetSeconds = 0;
	
	private boolean executeInThisInstance = true;
	private boolean executionFinished = false;
	

	private boolean isTerminated = false;
	
	private ArrayList<Thread> userThreadList = new ArrayList<>();
	
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRExecOnce(Class<? extends PFRUsecase> usecaseClass) {
		super(usecaseClass);
	}
	
	/*************************************************************************** 
	 * This method creates a standard load pattern:
	 * <ul>
	 * <li>Ramping up users at the start of the test</li>
	 * <li>Keeping users at a constant level</li>
	 * <li>Adds pacing to the use cases.</li>
	 * </ul>
	 *
	 * This method will calculate the pacing and ramp up interval based on the input
	 * values. 
	 * 
	 * <pre>
	 * <code>
	 * int pacingSeconds = 3600 / (execsHour / users);
	 * int rampUpInterval = pacingSeconds / users * rampUp;
	 * </code>
	 * </pre>
	 *
	 * @param offset    in seconds from the test start
	 * 
	 ***************************************************************************/
	public PFRExecOnce(Class<? extends PFRUsecase> usecase, long offset){

		this(usecase);
		
		this.offsetSeconds = offset;
		
	}
		
	/*****************************************************************
	 * Set the offset in seconds.
	 *****************************************************************/
	public PFRExecOnce offset(int offsetSeconds) {
		this.offsetSeconds = offsetSeconds;
		return this;
	}
		
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() {
		
		synchronized(logger) {
			// -----------------------------------------------
			// Log Warnings
			// -----------------------------------------------
			String sides = "=".repeat(16);
			String title = " Load Config: "+this.usecaseName()+" ";
			logger.info(sides + title + sides);
			
			
			// -----------------------------------------------
			// Log infos
			// -----------------------------------------------
			logger.info("Executor: " + this.getClass().getSimpleName() );
			logger.info("Usecase: " + this.usecaseName());
			logger.info("Start Offset: " + offsetSeconds);
			logger.info(sides.repeat(2) + "=".repeat( title.length()) ); // cosmetics, just because we can!
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void getSettings(JsonObject settings) {
		settings.addProperty("startOffsetSec", offsetSeconds);
	}
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public void executeThreads() {

		//-------------------------
		// Check do Execute
		if( ! executeInThisInstance ) {
			return;
		}
		
		//-------------------------
		// Create Scheduler
		ScheduledThreadPoolExecutor threadExecutor = getScheduledUserExecutor(1);

		try {
						
			//-------------------------
			// Start Single Threads

			try {
				Thread userThread = createUserThread();
				
					userThread.setName(this.usecaseName()+"-User");
					threadExecutor.schedule(
							  userThread
							, offsetSeconds
							, TimeUnit.SECONDS
						);
											
					userThreadList.add(userThread);
					
				HSR.increaseUsers(1);

			}catch (Exception e) {
				logger.warn(this.usecaseName()+": Error While starting User Thread.");
			}
				
			//--------------------------------
			// Wait for graceful stop
			while(!gracefulStopRequested && getCurrentTaskCount() > 0) {
				Thread.sleep(1000);
			}
			
			//--------------------------------
			// Initialize Graceful stop
			doGracefulStop(this.test().gracefulStop());
			
		}catch(InterruptedException e) {
			logger.info("User Thread interrupted.");
			HSR.decreaseUsers(1);
		}finally {
			
		}	
	}

		
	/*****************************************************************
	 * INTERNAL USE ONLY
	 *****************************************************************/
	@Override
	public void distributeLoad(int totalAgents, int agentIndex, int recursionIndex) {
		
		//
		if(agentIndex != 0) {
			executeInThisInstance = false;
		}
				
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public Thread createUserThread() {
		

		PFRUsecase usecase = this.getUsecaseInstance();
		usecase.initializeUser();
		
		return new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				try {
					
					try {
						usecase.execute();
						
						// make sure everything is closed
						HSR.endAllOpen(HSRRecordStatus.Aborted);
						
					}catch (Throwable e) {
						logger.error("Unhandled Exception occured.", e);
						HSR.endAllOpen(HSRRecordStatus.Failed);
					}
					
					
				}catch(Exception e) {
					logger.info("User Thread interrupted.");
				}finally {
					executionFinished = true;
				}
			}
		});
	}
	

	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void requestGracefulStop() {
		this.gracefulStopRequested = true;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void terminate() {
		
		if(!isTerminated) {
			isTerminated = true;
			for(Thread thread : userThreadList) {
				
				try {
					if(thread.isAlive() && !thread.isInterrupted()) {
						thread.interrupt();
					}
				}catch(Throwable e) {
					logger.error("Error while stopping user thread: " + e.getMessage(), e);
				}
			}
		}
			
		
	}
	
	

}
