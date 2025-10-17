package com.performetriks.performator.executors;

import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRExecutorStandard extends PFRExecutor {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecutorStandard.class.getName());
	
	private PFRUsecase usecase;

	private int users = 1;
	private int execsHour = 60;
	private long offset = 0;
	private int rampUp = 1;
	private int rampUpInterval = -1;
	private int pacingSeconds = -1;
	
	private boolean stopped = false;
	
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRExecutorStandard(PFRUsecase usecase) {
		super(usecase);
		
		this.usecase = usecase;
	}
	
	
	/***************************************************************************
	 * The scenario will be set to run forever, therefore make sure to
	 * set maxDuration in your simulation:
	 * 
	 * <pre>
	 * <code>
	 * setUp(...).maxDuration(Duration.ofMinutes(15))
	 * </code>
	 * </pre>
	 * 
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
	public PFRExecutorStandard(
						PFRUsecase usecase
						, int users
						, int execsHour
						, long offset
						, int rampUp
					){

		this(usecase);
		
		this.users = users;
		this.execsHour = execsHour;
		this.offset = offset;
		
		if(rampUp > users) { rampUp = users; } // prevent issues with ramp up
		this.rampUp = rampUp;
	}

	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize(PFRContext context) {
		
		// -----------------------------------------------
		// Calculate Load Parameters
		// -----------------------------------------------
		int pacingSeconds = (int)Math.ceil( 3600 / ( 1f * execsHour / users) );
		int rampUpInterval = (int)Math.ceil( (1f * pacingSeconds / users) * rampUp );
		
		this.rampUpInterval = rampUpInterval;
		this.pacingSeconds = pacingSeconds;
				
		// -----------------------------------------------
		// Log Warnings
		// -----------------------------------------------
		String sides = "=".repeat(16);
		String title = " Load Config: "+usecase.getName()+" ";
		logger.info(sides + title + sides);
		
		if(rampUpInterval == 0) {
			String message = "Calculated ramp up interval is 0, all users are started at the same time.";
			HSR.addWarnMessage(message);
			logger.warn("==> " + message);
		}
		
		if(pacingSeconds < 10) {
			String message = "Calculated pacing is below 10 seconds, make sure one iteration of your scenario can execute in that time.";
			HSR.addWarnMessage(message);
			logger.warn("==> "+message);
		}
		
		if(pacingSeconds == 0) {
			pacingSeconds = 1; 

			String message = "Calculated Pacing was 0 seconds, set to 1 second.";
			HSR.addWarnMessage(message);
			logger.warn("==> "+message);
		}
		
		// -----------------------------------------------
		// Log infos
		// -----------------------------------------------

		logger.info("Scenario: " + usecase);
		logger.info("Target Users: " + users);
		logger.info("Executions/Hour: " + execsHour);
		logger.info("StartOffset: " + offset);
		logger.info("RampUp Users: " + rampUp);
		logger.info("RampUp Interval(s): " + rampUpInterval);
		logger.info("Pacing(s): " + pacingSeconds);
		logger.info(sides.repeat(2) + "=".repeat( title.length()) ); // cosmetics, just because we can!
		
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public JsonObject getSettings(PFRContext context) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public void executeUsecase(PFRContext context) {
		
		//-------------------------
		// Nothing todo?
		if(users == 0 || execsHour == 0) {
			return;
		}
		
		//-------------------------
		// Create Threads
		CountDownLatch latch = new CountDownLatch(users);
		
		try {
			
			for(int i = 0; i < users; i ++) {
				
				Thread userThread = createUserThread(context, latch);
				
				userThread.setName("ExecutorStandard-User-"+i);
				userThread.start();
					
				HSR.increaseUsers(1);
				
				Thread.sleep(rampUpInterval * 1000);
				
			}
			
			latch.await();	
		}catch(InterruptedException e) {
			logger.info("User Thread interrupted.");
		}finally {
			latch.countDown();
			HSR.decreaseUsers(1);
		}	
	}
	
	/*****************************************************************
	 * INTERNAL USE ONLY
	 *****************************************************************/
	@Override
	public void distributeLoad(int totalAgents, int agentIndex, int recursionIndex) {
		
		int usersPerAgent = (int)Math.ceil((1.0f * users) / totalAgents);
		int execsPerAgent = (int)Math.ceil((1.0f * execsHour) / totalAgents);
		
		//----------------------------------
		// Calculate if the agent still has
		// users
		int remainingUsers = users;
		int remainingExecsHour = execsHour;
		for(int i = 0; i < agentIndex; i++) {
			remainingUsers -= usersPerAgent;
			remainingExecsHour -= execsPerAgent;
		}
		
		//----------------------------------
		// Set Users
		if(remainingUsers == 0) {
			users = 0;
			execsHour = 0;
		}
	}
	
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public Thread createUserThread(PFRContext context, CountDownLatch latch) {
		
		return new Thread(new Runnable() {
			@Override
			public void run() {
				
				try {
					
					while(!stopped){
						usecase.execute(context);
						Thread.sleep(pacingSeconds * 1000); 
					}

				}catch(InterruptedException e) {
					logger.info("User Thread interrupted.");
				}finally {
					latch.countDown();
					HSR.decreaseUsers(1);
				}
			}
		});
	}
	

	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void gracefulStop(PFRContext context) {
		this.stopped = true;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void terminate(PFRContext context) {
		// TODO Auto-generated method stub
		
	}
	
	

}
