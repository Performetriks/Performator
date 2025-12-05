package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRTest;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.base.HSRTestSettings;
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
public class PFRExecSequential extends PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecSequential.class.getName());
	
	private long offsetSeconds = 0;

	private boolean isTerminated = false;
	
	private ArrayList<Thread> executorThreadList = new ArrayList<>();
	
	private ArrayList<PFRExec> executorList = new ArrayList<>();;

	/*****************************************************************
	 * Constructor
	 * 
	 *****************************************************************/
	public PFRExecSequential() {
		// nothing todo
	}
	
	/*****************************************************************
	 * Constructor
	 * 
	 *****************************************************************/
	public PFRExecSequential(PFRExec... executors) {
		
		for(PFRExec executor : executors) {
			executorList.add(executor);
		}
		
	}

	/*****************************************************************
	 * Set the offset in seconds.
	 *****************************************************************/
	public PFRExecSequential add(PFRExec executor) {
		executorList.add(executor);
		return this;
	}
		
	/*****************************************************************
	 * Set the offset in seconds.
	 *****************************************************************/
	public PFRExecSequential offset(int offsetSeconds) {
		this.offsetSeconds = offsetSeconds;
		return this;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	@Override
	public PFRExec test(PFRTest test){
		super.test(test);
		
		for(PFRExec executor : executorList) {
			executor.test(test);
		}
		
		return this;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() {
		
		synchronized(logger) {
			// -----------------------------------------------
			// Log
			// -----------------------------------------------
			String sides = "=".repeat(16);
			String title = " Load Config: "+this.getExecutedName()+" ";
			logger.info(sides + title + sides);
			
			logger.info("Executor: " + this.getClass().getSimpleName() );
			logger.info("Executors: " + this.getExecutedName());
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
		
		
		for(PFRExec executor : executorList) {
			HSRConfig.addTestSettings(
					new HSRTestSettings(
							  executor.getExecutedName()
							, settings
						)
				);
		}
	}
	
	/*****************************************************************
	 * Return the name of the usecase or other thing that is 
	 * executed by this executor. 
	 * 
	 * @return the name of the usecase or null
	 *****************************************************************/
	public String getExecutedName() {
		
		StringBuilder builder = new StringBuilder("[");
		
		for(PFRExec executor : executorList) {
			builder
				.append(executor.getClass().getSimpleName())
				.append("("+executor.getExecutedName()+")")
				.append(",")
				;
		}
		
		builder.deleteCharAt(builder.length()-1).append("]");
		
		return builder.toString();
	}
	
	/*****************************************************************
	 * Execute the threads
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public void executeThreads() {
		
		//-------------------------
		// Create Scheduler
		ScheduledThreadPoolExecutor threadExecutor = getScheduledUserExecutor(1);

		try {
						
			//-------------------------
			// Start Single Threads

			try {
				Thread executorThread = createSequentialThread();
				
					executorThread.setName(this.getExecutedName()+"-User");
					threadExecutor.schedule(
							  executorThread
							, offsetSeconds
							, TimeUnit.SECONDS
						);
											
					executorThreadList.add(executorThread);

			}catch (Exception e) {
				logger.warn(this.getExecutedName()+": Error While starting User Thread.");
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
			logger.info("Executor Thread interrupted.");
		}finally {
			
		}	
		
	}

		
	/*****************************************************************
	 * INTERNAL USE ONLY
	 *****************************************************************/
	@Override
	public void distributeLoad(int totalAgents, int agentIndex, int recursionIndex) {
		
		for(PFRExec executor : executorList) {
			executor.distributeLoad(totalAgents, agentIndex, recursionIndex);
		}
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public Thread createSequentialThread() {
		
		return new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				try {
					
					try {
						for(PFRExec executor : executorList) {
							if(!gracefulStopRequested) {
								executor.execute();
							}
						}
						
					}catch (Throwable e) {
						logger.error("Unhandled Exception occured: "+e.getMessage(), e);
						HSR.endAllOpen(HSRRecordStatus.Failed);
					}
					
				}catch(Exception e) {
					logger.info("Executor Thread interrupted.");
				}
			}
		});
	}
	
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void requestGracefulStop() {
		super.requestGracefulStop();
		for(PFRExec exec : executorList) {
			exec.requestGracefulStop();
		}
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void terminate() {

		if(!isTerminated) {
			isTerminated = true;
			
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
					logger.error("Error while stopping user thread: " + e.getMessage(), e);
				}
			}
		}
			
		
	}
	
	

}
