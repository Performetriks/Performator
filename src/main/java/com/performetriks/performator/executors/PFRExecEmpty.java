package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.xresch.hsr.base.HSR;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * Empty Executor that runs until the max test duration is reached.
 * The task is executed every 60 seconds and does nothing except wait for 
 * 1 second.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRExecEmpty extends PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecEmpty.class.getName());
	
	
	private boolean isTerminated = false;
	
	private ArrayList<Thread> userThreadList = new ArrayList<>();
	
	private ScheduledExecutorService scheduledUserThreadExecutor;
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public PFRExecEmpty() { }
	
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() { /*do nothing*/ }
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void getSettings(JsonObject settings) {	/*do nothing*/ }
	
	/*****************************************************************
	 * Return the name of the usecase or other thing that is 
	 * executed by this executor. 
	 * 
	 * @return the name of the usecase or null
	 *****************************************************************/
	public String getExecutedName() { return "Empty"; }
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public void executeThreads() {
		
		//-------------------------
		// Create Scheduler
		int poolSize = 1; // Single-threaded scheduler for pacing

		scheduledUserThreadExecutor = getScheduledUserExecutor(poolSize);

		try {
			
			//-------------------------
			// Start Single Threads
			try {
				//--------------------------
				// Make thread
				Runnable task = createIterationRunnable(0);
					scheduledUserThreadExecutor.scheduleAtFixedRate(
							  task
							, 0
							, 60
							, TimeUnit.SECONDS
						);			
			}catch (Exception e) {
				HSR.addException(e);
				logger.warn(this.getExecutedName()+": Error While starting User Thread: " + e.getMessage(), e);
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
			Thread.currentThread().interrupt();
		    return; 
		}finally {
			
		}	
	}
	
	
	/*****************************************************************
	 * INTERNAL USE ONLY
	 *****************************************************************/
	@Override
	public void distributeLoad(int totalAgents, int agentIndex, int recursionIndex) {
		// nothing todo
	}
	
	/*****************************************************************
	 * Creates a Runnable for a user iteration.
	 *****************************************************************/
	public Runnable createIterationRunnable(final int userId) {
		
		//------------------------------
		// Wrapped task
		Runnable iterationTask = () -> {

			try {
				logger.debug("PFRExecEmpty executed.");
				Thread.sleep(1000); // do nothing
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			    return;  
			} 

		};

		//------------------------------
		// Scheduled Task		
		return new Runnable() {
			@Override
			public void run() {
				
				//---------------------------
				// Execute Virtual or Regular
				if (PFRExec.isVirtualThreadSupported()) {
					PFRExec.startVirtualThread(iterationTask, getExecutedName() + "-VT-" + userId);
				} else {
					iterationTask.run();
				}
			}
		};
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
					HSR.addException(e);
					logger.error("Error while stopping user thread: " + e.getMessage(), e);
				}
			}
		}
			
		
	}
	
	

}
