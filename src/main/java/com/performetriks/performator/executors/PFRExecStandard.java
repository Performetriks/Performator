package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * Executes a use case with a standard load pattern.
 * 
 * <ul>
 * <li>Ramping up users at the start of the test</li>
 * <li>Keeping users at a constant level</li>
 * <li>Adds pacing to the use cases.</li>
 * </ul>
 *
 * This class will calculate the pacing and ramp up interval based on the input
 * values. 
 * 
 * <pre>
 * <code>
 * int pacingSeconds = 3600 / (execsHour / users);
 * int rampUpInterval = pacingSeconds / users * rampUp;
 * </code>
 * </pre>
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRExecStandard extends PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecStandard.class.getName());
	
	private int percent = 100;
	private int users = 1;
	private int execsHour = 60;
	private long offsetSeconds = 0;
	private int rampUpUsers = 1;
	private int rampUpInterval = -1;
	private int pacingSeconds = -1;
	
	private boolean isTerminated = false;
	
	private ArrayList<Thread> userThreadList = new ArrayList<>();
	
	private ScheduledThreadPoolExecutor scheduledUserThreadExecutor;
	
	private Class<? extends PFRUsecase> usecaseClass;
	private String usecaseName;
	
	private boolean isCalculated = false;
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRExecStandard(Class<? extends PFRUsecase> usecaseClass) {
		this.usecaseClass = usecaseClass;
		PFRUsecase instance = PFRUsecase.getUsecaseInstance(usecaseClass);
		usecaseName = instance.getName();
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
	 * @param usecase 	the usecase to be executed with this executor
	 * @param users     number of users to run constantly for this scenario
	 * @param execsHour targeted number of executions per hour
	 * @param offset    in seconds from the test start
	 * @param rampUp    number of users to increase per ramp up
	 * 
	 ***************************************************************************/
	public PFRExecStandard(
						  Class<? extends PFRUsecase> usecase
						, int users
						, int execsHour
						, long offset
						, int rampUp
					){

		this(usecase);
		
		this.users = users;
		this.execsHour = execsHour;
		this.offsetSeconds = offset;
		
		if(rampUp > users) { rampUp = users; } // prevent issues with ramp up
		this.rampUpUsers = rampUp;
	}
	
	/*****************************************************************
	 * Set the number of users.
	 *****************************************************************/
	public PFRExecStandard users(int users) {
		this.users = users;
		return this;
	}
	
	/*****************************************************************
	 * Set the executions per hour.
	 *****************************************************************/
	public PFRExecStandard execsHour(int execsHour) {
		this.execsHour = execsHour;
		return this;
	}
	
	/*****************************************************************
	 * Set the offset in seconds.
	 *****************************************************************/
	public PFRExecStandard offset(int offsetSeconds) {
		this.offsetSeconds = offsetSeconds;
		return this;
	}
	
	/*****************************************************************
	 * Set the amount of users to add per ramp up interval.
	 *****************************************************************/
	public PFRExecStandard rampUp(int rampUp) {
		this.rampUpUsers = rampUp;
		return this;
	}
	
	/*****************************************************************
	 * Call this method after setting users and executions per hour
	 * to run a percentage of that load.
	 * 
	 * @param percent 100 is 100%, you can go lower or higher, e.g. 50% or 200%
	 *****************************************************************/
	public PFRExecStandard percent(int percent) {
		this.percent = percent;
		return this;
	}

	/*****************************************************************
	 * Calculates pacingSeconds and rampUpInterval
	 *****************************************************************/
	public void calculateLoadSettings() {
		
		if(!isCalculated) {
			// -----------------------------------------------
			// Apply Percentage
			// -----------------------------------------------
			if(percent != 100) {
				users = (int)Math.ceil( users * (percent / 100.0f) );
				execsHour = (int)Math.ceil( execsHour * (percent / 100.0f) );
			}
			
			// -----------------------------------------------
			// Calculate Load Parameters
			// -----------------------------------------------
			int pacingSeconds = (int)Math.ceil( 3600 / ( 1f * execsHour / users) );
			int rampUpInterval = (int)Math.ceil( (1f * pacingSeconds / users) * rampUpUsers );
			
			this.rampUpInterval = rampUpInterval;
			this.pacingSeconds = pacingSeconds;
			
			isCalculated = true;
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() {
		
		HSR.setUsecase(usecaseName);
		// -----------------------------------------------
		// Calculate Load Parameters
		// -----------------------------------------------
		calculateLoadSettings();
		
		synchronized(logger) {
			// -----------------------------------------------
			// Log Warnings
			// -----------------------------------------------
			String sides = "=".repeat(16);
			String title = " Load Config: "+this.getExecutedName()+" ";
			logger.info(sides + title + sides);
			
			if(rampUpInterval == 0) {
				String message = "Calculated ramp up interval is 0, all users are started at the same time.";
				HSR.addWarnMessage(message);
				logger.warn("==> " + message);
			}
			
			// too much false positives, other messages are written instead when pacing is exceeded
			/*if(pacingSeconds < 10) {
				String message = "Calculated pacing is below 10 seconds, make sure one iteration of your scenario can execute in that time.";
				HSR.addWarnMessage(message);
				logger.warn("==> "+message);
			}*/
			
			if(pacingSeconds == 0) {
				pacingSeconds = 1; 
	
				String message = "Calculated Pacing was 0 seconds, set to 1 second.";
				HSR.addWarnMessage(message);
				logger.warn("==> "+message);
			}
			
			// -----------------------------------------------
			// Log infos
			// -----------------------------------------------
			logger.info("Executor: " + this.getClass().getSimpleName() );
			logger.info("Usecase: " + this.getExecutedName());
			logger.info("Percent: " + percent);
			logger.info("Target Users: " + users);
			logger.info("Executions/Hour: " + execsHour);
			logger.info("Start Offset: " + offsetSeconds);
			logger.info("RampUp Users: " + rampUpUsers);
			logger.info("RampUp Interval(s): " + rampUpInterval);
			logger.info("Pacing(s): " + pacingSeconds);
			logger.info(sides.repeat(2) + "=".repeat( title.length()) ); // cosmetics, just because we can!
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void getSettings(JsonObject settings) {
		
		calculateLoadSettings();
		
		settings.addProperty("percent", percent);
		settings.addProperty("users", users);
		settings.addProperty("execPerHour", execsHour);
		settings.addProperty("startOffsetSec", offsetSeconds);
		settings.addProperty("rampUpUsers", rampUpUsers);
		settings.addProperty("rampUpIntervalSec", rampUpInterval);
		settings.addProperty("pacingSec", pacingSeconds);

	}
	
	/*****************************************************************
	 * Return the name of the usecase or other thing that is 
	 * executed by this executor. 
	 * 
	 * @return the name of the usecase or null
	 *****************************************************************/
	public String getExecutedName() {
		return usecaseName;
	}
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public void executeThreads() {
		
		//-------------------------
		// Nothing todo?
		if(users == 0 || execsHour == 0) {
			return;
		}

		//-------------------------
		// Create Scheduler
		scheduledUserThreadExecutor = getScheduledUserExecutor(users);

		try {
			
			//-------------------------
			// Create Threads
			if(offsetSeconds >= 0) {
				Thread.sleep(offsetSeconds * 1000);
			}
			
			//-------------------------
			// Start User Threads
			for(int i = 0; i < users && !gracefulStopRequested ; i++) {
				
				try {
					Thread userThread = createUserThread();
					
						userThread.setName(this.getExecutedName()+"-User-"+i);
						scheduledUserThreadExecutor.scheduleAtFixedRate(
								  userThread
								, 0
								, pacingSeconds
								, TimeUnit.SECONDS
							);
												
						userThreadList.add(userThread);
						
					HSR.increaseUsers(1);
					
					//--------------------------
					// Manage Ramp Up
					if( rampUpUsers > 0 
					&&  ( (i+1) % rampUpUsers ) == 0
					){
						Thread.sleep(rampUpInterval * 1000);
					}
					
				}catch (InterruptedException e) {
				    Thread.currentThread().interrupt();
				    return;                              
				}catch (Exception e) {
					HSR.addException(e);
					logger.warn(this.getExecutedName()+": Error While starting User Thread.");
				}
				
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
		
		//----------------------------------
		// Calculate Load Parameters
		// ---------------------------------
		calculateLoadSettings();
		
		int usersPerAgent = (int)Math.ceil((1.0f * users) / totalAgents);
		int execsPerAgent = (int)Math.ceil((1.0f * execsHour) / totalAgents);
		int offsetPerAgent = (int)Math.ceil((1.0f * pacingSeconds) / totalAgents);
		
		//----------------------------------
		// Calculate if the agent still has
		// users
		int remainingUsers = users;
		int remainingExecsHour = execsHour;
		int additionalOffset = 0;
		for(int i = 0; i < agentIndex; i++) {
			remainingUsers -= usersPerAgent;
			remainingExecsHour -= execsPerAgent;
			additionalOffset += offsetPerAgent;
		}
		
		
		//----------------------------------
		// Set New Values
		offsetSeconds += additionalOffset;
		
		if(remainingUsers == 0) {
			users = 0;
			execsHour = 0;
			return;
		}
		
		if(remainingUsers >= usersPerAgent) {
			users = usersPerAgent;
		}else {
			users = remainingUsers;
		}
		
		if(remainingExecsHour >= execsPerAgent) {
			execsHour = execsPerAgent;
		}else {
			execsHour = remainingExecsHour;
		}
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public Thread createUserThread() {
		
		int pacingMillis = pacingSeconds * 1000;
		
		PFRUsecase usecase = PFRUsecase.getUsecaseInstance(usecaseClass);
		
		usecase.initializeUser();
		
		return new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				try {
					
					long start = System.currentTimeMillis();
					
					try {
						usecase.execute();
						
						// make sure everything is closed
						HSR.endAllOpen(HSRRecordStatus.Aborted);
						
					}catch (InterruptedException e) {
					    Thread.currentThread().interrupt(); // prevent lingering of threads
					    return;                              
					}catch (Throwable e) {
						HSR.addException(e);
						logger.error("Unhandled Exception occured.", e);
						HSR.endAllOpen(HSRRecordStatus.Failed);
					}finally {
						PFRContext.logDetailsClear();
					}
					
					long duration = System.currentTimeMillis() - start;
					
					if(duration > pacingMillis)  {
						HSR.addWarnMessage("Duration of the iteration exceeded the pacing("+pacingSeconds+"s)."
										 + " This might cause that you get lower execution/hour then expected."
										 + " Increase the number of users to fix this if you get lots of these messages.");
					}
					
				}catch(Exception e) {
					logger.info("User Thread interrupted.");
				}
			}
		});
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
