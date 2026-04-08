package com.performetriks.performator.executors;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;

import ch.qos.logback.classic.Logger;

/*************************************************************************************************
 * Base class for Executors.
 * Implementations of this class are responsible for:
 * - Calling "HSR.endAllOpen(HSRRecordStatus.Aborted);" after executing a usecase iteration.
 * - Calling "PFRContext.logDetailsClear()" after executing a usecase iteration.
 * - Handling exceptions thrown by usecases.
 * - Calling "HSR.endAllOpen(HSRRecordStatus.Failed);" in case an exception was thrown.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 *************************************************************************************************/
public abstract class PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExec.class.getName());
	
	private PFRTest test;
	
	private Duration maxDuration = null;
	private Duration usecaseGracefulStopDuration = Duration.ofMinutes(1);
	
	protected Object GRACEFUL_LOCK = true;
	protected boolean gracefulStopRequested = false;  
	protected boolean gracefulStopDone = false;  
	protected boolean isStopNow = false;
	
	private ScheduledExecutorService scheduledUserThreadExecutor;
	
    // Optimized reflection cache for Virtual Thread support
    private static java.lang.reflect.Method ofVirtualMethod = null;
    private static java.lang.reflect.Method nameMethod = null;
    private static java.lang.reflect.Method unstartedMethod = null;
    private static boolean virtualThreadsAttempted = false;
    
    // Optimized reflection cache for PFRHttp plugin cleanup
    private static java.lang.reflect.Method pfrHttpResetMethod = null;
    private static boolean pfrHttpAttempted = false;

    public static synchronized void initializeVirtualThreadMethods() {
        if (virtualThreadsAttempted) return;
        virtualThreadsAttempted = true;
        try {
            ofVirtualMethod = Thread.class.getMethod("ofVirtual");
            Class<?> builderClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
            
            nameMethod = builderClass.getMethod("name", String.class);
            unstartedMethod = builderClass.getMethod("unstarted", Runnable.class);
            
            ofVirtualMethod.setAccessible(true);
            nameMethod.setAccessible(true);
            unstartedMethod.setAccessible(true);
            logger.info("Virtual Threads are supported and initialized.");
        } catch (Exception e) {
            // Check for newer API or specific distribution signature
            try {
                // Secondary check for direct virtual thread start
                Thread.class.getMethod("startVirtualThread", Runnable.class);
                logger.info("Virtual Threads supported via startVirtualThread.");
            } catch (Exception e2) {
                logger.info("Virtual Threads are NOT supported: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if Virtual Threads are supported by the current JVM.
     * @return true if supported, false otherwise
     */
    public static boolean isVirtualThreadSupported() {
        initializeVirtualThreadMethods();
        return (ofVirtualMethod != null);
    }

    /**
     * Starts a virtual thread using reflection if supported.
     * @param r the runnable to execute
     * @param name prefix for the thread name
     */
    public static void startVirtualThread(Runnable r, String name) {
        initializeVirtualThreadMethods();
        if (ofVirtualMethod != null) {
            try {
                Object builder = ofVirtualMethod.invoke(null);
                nameMethod.invoke(builder, name);
                Thread t = (Thread) unstartedMethod.invoke(builder, r);
                t.start();
            } catch (Exception e) {
                // Fallback to platform thread if reflection fails
                Thread t = new Thread(r);
                t.setName(name);
                t.start();
            }
        } else {
            Thread t = new Thread(r);
            t.setName(name);
            t.start();
        }
    }

    /**
     * Optimized call to PFRHttp.resetThreadState() using cached reflection lookup.
     */
    public static void resetPFRHttpState() {
        if (!pfrHttpAttempted) {
            synchronized (PFRExec.class) {
                if (!pfrHttpAttempted) {
                    pfrHttpAttempted = true;
                    try {
                        Class<?> pfrHttpClass = Class.forName("com.performetriks.performator.http.PFRHttp");
                        pfrHttpResetMethod = pfrHttpClass.getMethod("resetThreadState");
                    } catch (Exception e) {
                        // Plugin not present
                    }
                }
            }
        }
        
        if (pfrHttpResetMethod != null) {
            try {
                pfrHttpResetMethod.invoke(null);
            } catch (Exception e) {
                // Ignore unexpected invocation errors in cleanup
            }
        }
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
	 * @param recursionIndex can be used to call this method recursively first
	 * call from the framework will always be 0.
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
	
	/*****************************************************************
	 * Return the name of the usecase or other thing that is 
	 * executed by this executor. 
	 * 
	 * @return the name of the usecase or null
	 *****************************************************************/
	public abstract String getExecutedName();
	
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
	 * To hell with graceful! Just stop it already!
	 * 
	 *****************************************************************/
	public void doStopNow()  {
		
		isStopNow = true;
		doGracefulStop(Duration.ofMillis(0));
		
	}
	
	/*****************************************************************
	 * Returns the amount of tasks that has not yet finished.
	 *****************************************************************/
	protected int getCurrentTaskCount() {
        if(scheduledUserThreadExecutor instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor)scheduledUserThreadExecutor;
            return executor.getActiveCount() + executor.getQueue().size();
        }
        return 0;
	}
	
	/*****************************************************************
	 * Returns the scheduled thread pool executor for this executor
	 * instance.
	 *****************************************************************/
	protected ScheduledExecutorService getScheduledUserExecutor(int threadPoolSize) {
		
		if(scheduledUserThreadExecutor == null) {
			String executorName = this.getClass().getSimpleName();
			ThreadFactory factory =  new ThreadFactory() {
			    private final AtomicInteger count = new AtomicInteger(1);
	
			    @Override
			    public Thread newThread(Runnable r) {
                    initializeVirtualThreadMethods();
                    
                    if (ofVirtualMethod != null) {
                        try {
                            Object builder = ofVirtualMethod.invoke(null);
                            nameMethod.invoke(builder, executorName + "-User-" + count.getAndIncrement());
                            return (Thread) unstartedMethod.invoke(builder, r);
                        } catch (Exception e) {
                            // Fallback on unexpected reflection error
                        }
                    }

                    // Fallback to Platform Thread for Java 17 and older
                    Thread t = new Thread(r);
                    t.setName(executorName+"-User-" + count.getAndIncrement());
                    t.setDaemon(true); 
                    return t;
			    }
			};
			
			scheduledUserThreadExecutor = Executors.newScheduledThreadPool(threadPoolSize, factory);
			
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
				while( previousTasksCount > 0 
				    && (shutdownEnd - shutdownStart) <= graceMillis 
				    && !isStopNow ) {
					Thread.sleep(100);
					
					int currentTasksCount = getCurrentTaskCount();
					HSR.decreaseUsers(previousTasksCount - currentTasksCount);
					
					previousTasksCount = currentTasksCount;
					
				}
				
				//--------------------------------
				// Stop it
				scheduledUserThreadExecutor.awaitTermination(1, TimeUnit.SECONDS);
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // restore interrupt flag
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
		
		// this must now be done by each executor itself
		//HSR.setUsecase(getExecutedName());
		
		initialize();
		
			createSelfStopper();
			executeThreads();
		
		terminate();
	}

	
}
