package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
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
	
	private boolean gracefulStopRequested = false;  
	private boolean isTerminated = false;
	
	
	private ArrayList<Thread> userThreadList = new ArrayList<>();
	
	private ScheduledThreadPoolExecutor scheduledUserThreadExecutor;
	
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
		String executorName = this.getClass().getSimpleName();
		ThreadFactory factory =  new ThreadFactory() {
		    private final AtomicInteger count = new AtomicInteger(1);

		    @Override
		    public Thread newThread(Runnable r) {
		        Thread t = new Thread(r);
		        t.setName(executorName+"-User-" + count.getAndIncrement());
		        return t;
		    }
		};
		
		scheduledUserThreadExecutor = 
				(ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(1, factory);

		try {
						
			//-------------------------
			// Start Single Threads

			try {
				Thread userThread = createUserThread();
				
					userThread.setName(this.usecaseName()+"-User");
					scheduledUserThreadExecutor.schedule(
							  userThread
							, offsetSeconds
							, TimeUnit.SECONDS
						);
											
					userThreadList.add(userThread);
					
				HSR.increaseUsers(1);

			}catch (Exception e) {
				HSR.addException(e);
				logger.warn(this.usecaseName()+": Error While starting User Thread.");
			}
				
			//--------------------------------
			// Wait for graceful stop
			while(!gracefulStopRequested && getCurrentTaskCount() > 0) {
				Thread.sleep(1000);
			}
			
			//--------------------------------
			// Initialize Graceful stop		
			scheduledUserThreadExecutor.shutdown();
			
			int previousTasksCount = getCurrentTaskCount();
			
			//--------------------------------
			// Wait for Stopping
			long shutdownStart = System.currentTimeMillis();
			long shutdownEnd = shutdownStart;
			long graceMillis = this.test().gracefulStop().getSeconds();
			while( previousTasksCount > 0 && (shutdownEnd - shutdownStart) <= graceMillis ) {
				Thread.sleep(1000);
				
				int currentTasksCount = getCurrentTaskCount();
				HSR.decreaseUsers(previousTasksCount - currentTasksCount);
				
				previousTasksCount = currentTasksCount;
				
			}
			
			scheduledUserThreadExecutor.awaitTermination(
					  this.test().gracefulStop().getSeconds()
					, TimeUnit.SECONDS
				);
			
		}catch(InterruptedException e) {
			logger.info("User Thread interrupted.");
			HSR.decreaseUsers(1);
		}finally {
			
		}	
	}
	
	/*****************************************************************
	 * Returns the amount of tasks that has not yet finished.
	 *****************************************************************/
	private int getCurrentTaskCount() {
		return scheduledUserThreadExecutor.getActiveCount()
		+ scheduledUserThreadExecutor.getQueue().size();
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
						HSR.addException(e);
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
	public void gracefulStop() {
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
					HSR.addException(e);
					logger.error("Error while stopping user thread: " + e.getMessage(), e);
				}
			}
		}
			
		
	}
	
	

}
