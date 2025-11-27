package com.performetriks.performator.executors;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRTest;
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
public abstract class PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExec.class.getName());
	
	private PFRTest test;
	private Class<? extends PFRUsecase> usecaseClass;
	private String usecaseName;
	
	
	private Duration maxDuration = null;
	private Duration usecaseGracefulStopDuration = Duration.ofMinutes(1);
	
	protected Object GRACEFUL_LOCK = true;
	protected boolean gracefulStopRequested = false;  
	protected boolean gracefulStopDone = false;  
	
	private ScheduledThreadPoolExecutor scheduledUserThreadExecutor;
	
	/*****************************************************************
	 * Constructor
	 * 
	 *****************************************************************/
	public PFRExec(Class<? extends PFRUsecase> usecaseClass){
		this.usecaseClass = usecaseClass;
		this.usecaseName = this.getUsecaseInstance().getName();
	}
	
	
	/*****************************************************************
	 * This method will be executed by agents to calculate the amount
	 * of load that a specific agent will execute.
	 * It will be executed before initialize() is called.
	 * 
	 * The agentNumber is 1 for the first 2 for the second etc... and
	 * can be used to calculate the load a specific agent will hold.
	 * 
	 * @param totalAgent agent count
	 * @param agentIndex the number of the agent
	 * @param recursionIndex can be used to call this method recursively.
	 * 
	 *****************************************************************/
	public abstract void distributeLoad(int totalAgents, int agentIndex, int recursionIndex);
	
	/*****************************************************************
	 * Method to do any kind of initialization before executeUsecase()
	 * is called.
	 *****************************************************************/
	public abstract void initialize();	
	
	
	/*****************************************************************
	 * This method will start and manage the users of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public abstract void executeThreads();
	
	
	/*****************************************************************
	 * Method to do any kind of cleanup tasks etc.
	 * This will not stop the execution, use doGracefulStop() or
	 * requestGracefulStop() to initiate the shutdown of the execution.
	 * 
	 *****************************************************************/
	public abstract void terminate();
	
	/*****************************************************************
	 * 
	 * @return usecase
	 *****************************************************************/
	public PFRUsecase getUsecaseInstance(){
		
		try {
			return (PFRUsecase) usecaseClass.getDeclaredConstructor()
		    		.newInstance();

		} catch (Exception e) {
			logger.error("Error while creating instance for class "+usecaseClass.getName(), e);
		}
		
		return null;
		
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public PFRExec test(PFRTest test){
		this.test = test;
		return this;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public PFRTest test(){
		return test;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public String usecaseName(){
		return usecaseName;
	}
	
	/*****************************************************************
	 * Add values to the parameter passed to this method, with key-value 
	 * pairs that give details about the settings of this executor.
	 * 
	 * This will be executed before distributeLoad() and initialize().
	 * If do any calculations in initialize() for the load, it is 
	 * recommended to do these calculations in a separate method.
	 * 
	 * @param JsonObject object to add settings to
	 *****************************************************************/
	public abstract void getSettings(JsonObject object);	
	
	/***************************************************************************
	 * Sets the maximum duration of the test, default is 1 hour.
	 * @param maxDuration
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRExec maxDuration(Duration maxDuration){
		this.maxDuration = maxDuration;
		return this;
	}
	
	/***************************************************************************
	 * 
	 * @return duration
	 ***************************************************************************/
	public Duration maxDuration(){
		return maxDuration;
	}
	
	/***************************************************************************
	 * Duration for gracefully stopping the test. Default is 1 minute.
	 * This time is added after max duration has been reached.
	 * 
	 * @param maxDuration
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRExec gracefulStop(Duration gracefulStop){
		this.usecaseGracefulStopDuration = gracefulStop;
		return this;
	}
	
	/***************************************************************************
	 * Returns the graceful stop duration.
	 * @return duration
	 ***************************************************************************/
	public Duration gracefulStop(){
		return usecaseGracefulStopDuration;
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	public void requestGracefulStop() {
		this.gracefulStopRequested = true;
	}
	
	/*****************************************************************
	 * Returns the amount of tasks that has not yet finished.
	 *****************************************************************/
	protected int getCurrentTaskCount() {
		return scheduledUserThreadExecutor.getActiveCount()
		+ scheduledUserThreadExecutor.getQueue().size();
	}
	
	/*****************************************************************
	 * Returns the scheduled thread pool executor for this executor
	 * instance.
	 *****************************************************************/
	protected ScheduledThreadPoolExecutor getScheduledUserExecutor(int threadPoolSize) {
		
		if(scheduledUserThreadExecutor == null) {
			String executorName = this.getClass().getSimpleName();
			ThreadFactory factory =  new ThreadFactory() {
			    private final AtomicInteger count = new AtomicInteger(1);
	
			    @Override
			    public Thread newThread(Runnable r) {
			        Thread t = new Thread(r);
			        t.setName(executorName+"-User-" + count.getAndIncrement());
			        t.setDaemon(true); // prevent thread from blocking the JVM to stop
			        return t;
			    }
			};
			
			scheduledUserThreadExecutor = (ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(threadPoolSize, factory);
			
			//-------------------------------
			// Make sure to kill that pest
			String name = this.getClass().getSimpleName();
			Runtime.getRuntime().addShutdownHook(
				new Thread(() -> {
					logger.info("Terminate thread pool for "+ name);
					scheduledUserThreadExecutor.shutdownNow();
				})
			);
		}
		
		return scheduledUserThreadExecutor;
	
	}
	
	/*****************************************************************
	 * Do the graceful stopping.
	 * 
	 *****************************************************************/
	protected void doGracefulStop(Duration waitTime)  {
		
		if(waitTime == null) { waitTime = Duration.ofMillis(0); }
		
		gracefulStopRequested = true;
		
		synchronized (GRACEFUL_LOCK) {
			
			if(gracefulStopDone) {return; }
			
			scheduledUserThreadExecutor.shutdown();
			
			int previousTasksCount = getCurrentTaskCount();
			try {
				//--------------------------------
				// Wait Gracefully for Stopping
				long shutdownStart = System.currentTimeMillis();
				long shutdownEnd = shutdownStart;
				long graceMillis = waitTime.getSeconds();
				while( previousTasksCount > 0 && (shutdownEnd - shutdownStart) <= graceMillis ) {
					Thread.sleep(1000);
					
					int currentTasksCount = getCurrentTaskCount();
					HSR.decreaseUsers(previousTasksCount - currentTasksCount);
					
					previousTasksCount = currentTasksCount;
					
				}
				
				//--------------------------------
				// Stop it
				scheduledUserThreadExecutor.awaitTermination(1, TimeUnit.SECONDS);
				
			} catch (InterruptedException e) {
				// do nothing
			}finally {
				gracefulStopDone = true;
			}
		}
	}
	
	

	/*****************************************************************
	 * Creates a self stopping thread in case maxDuration was set.
	 *****************************************************************/
	private void createSelfStopper() {
		
		if(maxDuration == null) { return; }			
		
		PFRExec instance = this;
		Timer timer = new Timer(true);
		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				instance.doGracefulStop(usecaseGracefulStopDuration);
			}
		}
		, maxDuration.getSeconds() * 1000);
		
										
	}
	
	
	/*****************************************************************
	 * This method will start and manage the threads of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public void execute() {
		
		HSR.setUsecase(usecaseName);
		
		initialize();
		
			createSelfStopper();
			executeThreads();
		
		terminate();
	}

	
}
