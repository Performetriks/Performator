package com.performetriks.performator.executors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;
import com.xresch.xrutils.data.XRRecord;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.util.Duration;

/***************************************************************************
 * Executes a use case with a standard load pattern.
 * 
 * <ul>
 * <li>Define custom patterns using a fluent API (rampUp, start, stable, etc.)</li>
 * <li>Supports graceful and immediate shutdowns</li>
 * <li>Adds pacing to the use cases.</li>
 * </ul>
 *
 * Executes a use case with a custom load pattern.
 *  
 * 
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * 
 ***************************************************************************/
// Providing configuration to Executors
public class PFRExecCustom extends PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecCustom.class.getName());
	
    private final ArrayList<ModificationType> modifications = new ArrayList<>();
    private final ArrayList<XRRecord> modificationSettings = new ArrayList<>();    
	private long offsetSeconds = 0;
	private int percent = 100;

	protected enum ModificationType { 
	        START
	      , STOP // stop amount of users immediately
	      , RAMPUP
	      , RAMPDOWN
	      , STABLE
	      , KILLALL // kill all users that are currently running
	      };
	      
	
	private boolean isTerminated = false;
	private ScheduledThreadPoolExecutor scheduledUserThreadExecutor;
	private Class<? extends PFRUsecase> usecaseClass;
	private String usecaseName;
//	private ScheduledFuture<?> future;
	
	// Scheduler used for delayed graceful kills so we don't block the main pattern thread
    private ScheduledExecutorService gracefulScheduler = Executors.newScheduledThreadPool(1);
    
    // Central storage for running user tasks
    private ArrayList<ScheduledFuture<?>> futureList = new ArrayList<>();
    
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * @param usecaseClass 
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRExecCustom(Class<? extends PFRUsecase> usecaseClass) {
		this.usecaseClass = usecaseClass;
		PFRUsecase instance = PFRUsecase.getUsecaseInstance(usecaseClass);
		usecaseName = instance.getName();
	}
	
	
	/* Added newly - Start */
	/**
	 * 
	 * This is the rampUp method
	 * 
	 *
	 * @param numUsers
	 * @param userPerInterval
	 * @param rampUpInterval
	 * @return
	 * @return PFRExecCustom
	 */
	public PFRExecCustom rampUp(int numUsers, int userPerInterval, int rampUpInterval) {
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("userPerInterval", userPerInterval);
        xrrSettings.add("rampUpInterval", rampUpInterval);
        xrrSettings.add("pacingSeconds", 0); // No pacing
        this.modifications.add(ModificationType.RAMPUP);
        this.modificationSettings.add(xrrSettings);
        return this;
    }

	/**
	 * 
	 * This is the rampUpPaced method
	 * Calculates ramp up interval internally to fit the pacing
	 *
	 * @param numUsers
	 * @param userPerInterval
	 * @param pacingSeconds 
	 * @return
	 * @return PFRExecCustom
	 */
	public PFRExecCustom rampUpPaced(int numUsers, int userPerInterval, int pacingSeconds) {
        // Calculate ramp up interval internally to fit the pacing
        int rampUpInterval = (int) Math.ceil((1.0 * pacingSeconds / numUsers) * userPerInterval);
        
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("userPerInterval", userPerInterval);
        xrrSettings.add("pacingSeconds", pacingSeconds);
        xrrSettings.add("rampUpInterval", rampUpInterval);
        
        this.modifications.add(ModificationType.RAMPUP);
        this.modificationSettings.add(xrrSettings);
        return this;
    }	
	
    /**
     * 
     * This is the rampUpExec method
     * Calculates pacing and ramp up interval internally
     *
     * @param numUsers
     * @param userPerInterval
     * @param execsPerHour
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom rampUpExec(int numUsers, int userPerInterval, int execsPerHour) {
        // Calculate pacing and ramp up interval internally
        int pacingSeconds = (int) Math.ceil(3600.0 / ((1.0 * execsPerHour) / numUsers));
        int rampUpInterval = (int) Math.ceil((1.0 * pacingSeconds / numUsers) * userPerInterval);
        
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("userPerInterval", userPerInterval);
        xrrSettings.add("execsPerHour", execsPerHour);
        xrrSettings.add("pacingSeconds", pacingSeconds);
        xrrSettings.add("rampUpInterval", rampUpInterval);
        xrrSettings.add("paced", true);
        
        this.modifications.add(ModificationType.RAMPUP);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the rampUpExec method
     * Handles the RAMPUP modification type
     *
     * @param numUsers
     * @param userPerInterval
     * @param execsPerHour
     * @return
     * @return PFRExecCustom
     */
    private void executeRampUpThread(XRRecord xrRecord) {
        int numUsers = xrRecord.getInteger("numUsers");
        int userPerInterval = xrRecord.getInteger("userPerInterval");
        int rampUpInterval = xrRecord.getInteger("rampUpInterval");
        int pacingSeconds = xrRecord.containsKey("pacingSeconds") ? xrRecord.getInteger("pacingSeconds") : 0;
        
        for(int i = 0; i < numUsers && !gracefulStopRequested ; i++) {
            try {
                Thread userThread = createUserThread(pacingSeconds);
                userThread.setName(this.getExecutedName()+"-User-"+i);
                
                // Submit to executor and track future
                ScheduledFuture<?> future = (ScheduledFuture<?>) scheduledUserThreadExecutor.submit(userThread);
                futureList.add(future);
                
                HSR.increaseUsers(1);
                
                //--------------------------
                // Manage Ramp Up Interval
                if( userPerInterval > 0 && ( (i+1) % userPerInterval ) == 0 ){
                    Thread.sleep(rampUpInterval * 1000L);
                }
                
            }catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;                              
            }catch (Exception e) {
                HSR.addException(e);
                logger.warn(this.getExecutedName()+": Error While starting User Thread.");
            }
        }
    }
    
    /**
     * 
     * This is the start method
     * Starts numUsers at same time, no pacing
     *
     * @param numUsers
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom start(int numUsers) {
    	//starts numUsers at same time, no pacing
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("pacingSeconds", 0);
        this.modifications.add(ModificationType.START);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    
    /**
     * 
     * This is the start method
     * starts numUsers at same time, calculate pacing internally
     *
     * @param numUsers
     * @param execsPerHour
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom start(int numUsers, int execsPerHour) {
    	// starts numUsers at same time, calculate pacing internally
    	int pacingSeconds = 3600 / (execsPerHour / numUsers);
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("execsPerHour", execsPerHour);
        xrrSettings.add("pacingSeconds", pacingSeconds);
        xrrSettings.add("paced", true);
        this.modifications.add(ModificationType.START);
        this.modificationSettings.add(xrrSettings);
        return this;
    }

    /**
     * 
     * This is the stable method
     * Keeps stable for defined time
     *
     * @param millis
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom stable(long millis) {
    	// keep stable for defined time
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("durationMillis", millis);
        this.modifications.add(ModificationType.STABLE);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the stable method
     * 
     *
     * @param d
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom stable(Duration d) {
    	XRRecord xrrSettings = new XRRecord();
    	xrrSettings.add("durationMillis", d.getMilliseconds());
    	this.modifications.add(ModificationType.STABLE);
    	this.modificationSettings.add(xrrSettings);
    	return this;
    }
    /**
     * 
     * This is the rampDownGracefully method
     * Stop amount of users gracefully and gradually
     *
     * @param numUsers
     * @param userPerInterval
     * @param gracefulTimeSec
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom rampDownGracefully(int numUsers, int userPerInterval, long gracefulTimeSec) {
    	// stop amount of users gracefully and gradually
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("userPerInterval", userPerInterval);
        xrrSettings.add("gracefulTimeSec", gracefulTimeSec);
        xrrSettings.add("graceful", true);
        this.modifications.add(ModificationType.RAMPDOWN);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the rampDown method
     * Stop amount of users gradually and immediately
     *
     * @param numUsers
     * @param userPerInterval
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom rampDown(int numUsers, int userPerInterval) {
    	// stop amount of users gradually and immediately
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("userPerInterval", userPerInterval);
        xrrSettings.add("graceful", false);
        this.modifications.add(ModificationType.RAMPDOWN);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the stopGracefully method
     * 
     *
     * @param numUsers
     * @param gracefulTimeSec
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom stopGracefully(int numUsers, long gracefulTimeSec) {
    	// stop amount of users all at once but gracefully
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("gracefulTimeSec", gracefulTimeSec);
        xrrSettings.add("graceful", true);
        this.modifications.add(ModificationType.STOP);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the stop method
     * 
     *
     * @param numUsers
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom stop(int numUsers) {
    	// stop amount of users immediately
        XRRecord xrrSettings = new XRRecord();
        xrrSettings.add("numUsers", numUsers);
        xrrSettings.add("graceful", false);
        this.modifications.add(ModificationType.STOP);
        this.modificationSettings.add(xrrSettings);
        return this;
    }
    /**
     * 
     * This is the killAll method
     * 
     *
     * @return
     * @return PFRExecCustom
     */
    public PFRExecCustom killAll() {
    	// kill all users that are currently running
    	this.modifications.add(ModificationType.KILLALL);
    	this.modificationSettings.add(new XRRecord()); // empty record
        return this;
    }
    
	public void executeThreads() {
		
		if (offsetSeconds > 0) {
            try {
                Thread.sleep(offsetSeconds * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        scheduledUserThreadExecutor = getScheduledUserExecutor(0);

        try {
            Iterator<ModificationType> typeIter = modifications.iterator();
            Iterator<XRRecord> settingsIter = modificationSettings.iterator();

            while (typeIter.hasNext() && !gracefulStopRequested && !isTerminated) {
                ModificationType modification = typeIter.next();
                XRRecord settings = settingsIter.next();

                switch (modification) {
                    case START:
                        executeStartThread(settings);
                        break;
                    case RAMPUP:
                        executeRampUpThread(settings);
                        break;
                    case STABLE:
                        executeStableThread(settings);
                        break;
                    case RAMPDOWN:
                        executeRampDownThread(settings);
                        break;
                    case STOP:
                        executeStopThread(settings);
                        break;
                    case KILLALL:
                        executeKillAllThread();
                        break;
                    default:
                        logger.warn("Unknown modification type: {}", modification);
                }
            }
            
            //--------------------------------
            // Wait for graceful stop signal
            while(!gracefulStopRequested && getCurrentTaskCount() > 0) {
                Thread.sleep(1000);
            }
            
            //--------------------------------
            // Execute global Graceful stop		
            doGracefulStop(this.test().gracefulStop());
            
        }catch(InterruptedException e) {
            logger.info("User Thread interrupted.");
            HSR.decreaseUsers(1);
            Thread.currentThread().interrupt();
            return; 
        }finally {
            gracefulScheduler.shutdownNow();
        }	
	}
	
	/**
	 * 
	 * This is the executeStartThread method
	 * Handles the START modification type
	 *
	 * @param xrRecord
	 * @return void
	 */
	private void executeStartThread(XRRecord xrRecord) {
	    int numUsers = xrRecord.getInteger("numUsers");
	    int pacingSeconds = 0;
	    if(xrRecord.containsKey("pacingSeconds")) {
	    	pacingSeconds = xrRecord.getInteger("pacingSeconds");
	    }
	    for (int i = 0; i < numUsers && !gracefulStopRequested; i++) {
	        try {
	            Thread userThread = createUserThread(pacingSeconds); 
	            userThread.setName(this.getExecutedName() + "-User-" + System.currentTimeMillis() + "-" + i);
	            
	            ScheduledFuture<?> future = scheduledUserThreadExecutor.scheduleAtFixedRate(userThread, 0, 0, TimeUnit.SECONDS);
	            futureList.add(future);
	            HSR.increaseUsers(1);
	        } catch (Exception e) {
	            HSR.addException(e);
	            logger.warn(this.getExecutedName() + ": Error while starting user thread.");
	        }
	    }
	}
	/**
	 * 
	 * This is the executeStableThread method
	 * Handles the STABLE modification type
	 *
	 * @param xrRecord
	 * @return void
	 */
    private void executeStableThread(XRRecord xrRecord) {
        // Fixed key to match what is actually stored in stable()
        long millis = xrRecord.containsKey("durationMillis") ? xrRecord.getLong("durationMillis") : 0; 
        millis = xrRecord.containsKey("millis") ? xrRecord.getLong("millis") : 0; 
        
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
	/**
	 * 
	 * This is the executeRampDownThread method
	 * Handles the RAMPDOWN modification type
	 *
	 * @param xrRecord
	 * @return void
	 */
    private void executeRampDownThread(XRRecord xrRecord) {
        int numUsers = xrRecord.getInteger("numUsers");
        int userPerInterval = xrRecord.getInteger("userPerInterval");
        boolean isGraceful = xrRecord.containsKey("graceful");
        long gracefulTimeMs = isGraceful ? xrRecord.getLong("gracefulTimeSec") * 1000L : 0;
        
        int stopped = 0;
        while (stopped < numUsers && !futureList.isEmpty() && !gracefulStopRequested) {
            int batchSize = Math.min(userPerInterval, numUsers - stopped);
            for (int i = 0; i < batchSize && !futureList.isEmpty(); i++) {
                ScheduledFuture<?> future = futureList.remove(futureList.size() - 1);
                
                if (isGraceful) {
                    // Cancel without interrupting immediately
                    future.cancel(false);
                    // Schedule a hard kill after the graceful time
                    gracefulScheduler.schedule(() -> {
                        if(!future.isDone()) future.cancel(true);
                        HSR.decreaseUsers(1);
                    }, gracefulTimeMs, TimeUnit.MILLISECONDS);
                } else {
                    // Immediate stop
                    future.cancel(true);
                    HSR.decreaseUsers(1);
                }
                stopped++;
            }
            
            // Wait between batches if we haven't stopped enough users yet
            if (stopped < numUsers && userPerInterval > 0) {
                try {
                    Thread.sleep(1000); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
	/**
	 * 
	 * This is the executeStopThread method
	 * Handles the STOP modification type
	 *
	 * @param xrRecord
	 * @return void
	 */
    private void executeStopThread(XRRecord xrRecord) {
        int numUsers = xrRecord.getInteger("numUsers"); 
        boolean isGraceful = xrRecord.containsKey("graceful");
        long gracefulTimeMs = isGraceful ? xrRecord.getLong("gracefulTimeSec") * 1000L : 0;
 
        ArrayList<ScheduledFuture<?>> usersBeingCancelled = new ArrayList<>();
        
        for (int i = 0; i < numUsers && !futureList.isEmpty(); i++) {
        	ScheduledFuture<?> future = futureList.remove(futureList.size() - 1);
            usersBeingCancelled.add(future);
 
            if (!isGraceful) {
                future.cancel(true); // Immediate interrupt
                HSR.decreaseUsers(1);
            } else {
                future.cancel(false); // Let it finish naturally initially
            }
        }
 
        if (isGraceful) {
            // Schedule the forced interruption after the graceful timeout
            gracefulScheduler.schedule(() -> {
                for(ScheduledFuture<?> user : usersBeingCancelled){
                    if(!user.isDone()){ 
                        user.cancel(true); // Force kill
                    }
                    HSR.decreaseUsers(1); // Decrease stats when forcefully killed
                }
            }, gracefulTimeMs, TimeUnit.MILLISECONDS);
        }
    }
	


	/**
	 * 
	 * This is the executeKillAllThread method
	 * Handles the KILLALL modification type
	 *
	 * @return void
	 */
	public void executeKillAllThread() {
		for (ScheduledFuture<?> future : futureList) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
        HSR.decreaseUsers(futureList.size());
        futureList.clear();
	}
	
	/*****************************************************************
	 * Returns the number of current users.
	 * @return 
	 *****************************************************************/
	public int getCurrentUserCount() {
		return futureList.size();
	}
	/*****************************************************************
	 * Set the offset in seconds.
	 * @param offsetSeconds 
	 * @return 
	 *****************************************************************/
	public PFRExecCustom offset(int offsetSeconds) {
		this.offsetSeconds = offsetSeconds;
		return this;
	}
	
	
	/*****************************************************************
	 * Call this method after setting users and executions per hour
	 * to run a percentage of that load.
	 * 
	 * @param percent 100 is 100%, you can go lower or higher, e.g. 50% or 200%
	 * @return 
	 *****************************************************************/
	public PFRExecCustom percent(int percent) {
		this.percent = percent;
		return this;
	}

	/*****************************************************************
	 * Calculates pacingSeconds and rampUpInterval
	 *****************************************************************/
//	public void calculateLoadSettings() {
//		
//		if(!isCalculated) {
//			// -----------------------------------------------
//			// Apply Percentage
//			// -----------------------------------------------
//			if(percent != 100) {
//				users = (int)Math.ceil( users * (percent / 100.0f) );
//				execsHour = (int)Math.ceil( execsHour * (percent / 100.0f) );
//			}
//			
//			// -----------------------------------------------
//			// Calculate Load Parameters
//			// -----------------------------------------------
//			int pacingSeconds = (int)Math.ceil( 3600 / ( 1f * execsHour / users) );
//			int rampUpInterval = (int)Math.ceil( (1f * pacingSeconds / users) * rampUpUsers );
//			
//			this.rampUpInterval = rampUpInterval;
//			this.pacingSeconds = pacingSeconds;
//			
//			isCalculated = true;
//		}
//	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() {
		
		HSR.setUsecase(usecaseName);
        
        // -----------------------------------------------
        // Apply Percentage to Settings
        // -----------------------------------------------
        if(percent != 100) {
            for(XRRecord settings : modificationSettings) {
                if (settings.containsKey("numUsers")) {
                    int users = settings.getInteger("numUsers");
                    settings.add("numUsers", (int)Math.ceil(users * (percent / 100.0f)));
                }
                if (settings.containsKey("execsPerHour")) {
                    int execs = settings.getInteger("execsPerHour");
                    settings.add("execsPerHour", (int)Math.ceil(execs * (percent / 100.0f)));
                }
            }
        }

        synchronized(logger) {
            String sides = "=".repeat(16);
            String title = " Load Config: "+this.getExecutedName()+" ";
            logger.info(sides + title + sides);
            logger.info("Executor: " + this.getClass().getSimpleName() );
            logger.info("Usecase: " + this.getExecutedName());
            logger.info("Percent: " + percent);
            logger.info(sides.repeat(2) + "=".repeat( title.length()) ); 
        }
	}
	
	@Override
    public void getSettings(JsonObject settings) {
        settings.addProperty("offsetSeconds", offsetSeconds);
        settings.addProperty("percent", percent);
        // Could serialize modifications array here if needed
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
	 * INTERNAL USE ONLY
	 *****************************************************************/
	@Override
    public void distributeLoad(int totalAgents, int agentIndex, int recursionIndex) {
        // Simple distribution: divide user counts proportionally.
        if (totalAgents <= 1) return;

        for (XRRecord xrrSettings : modificationSettings) {
            if (xrrSettings.containsKey("numUsers")) {
                int users = xrrSettings.getInteger("numUsers");
                int usersPerAgent = (int) Math.ceil((1.0 * users) / totalAgents);
                int remaining = users - (agentIndex * usersPerAgent);
                if (remaining <= 0) {
                	xrrSettings.add("numUsers", 0);
                } else {
                	xrrSettings.add("numUsers", Math.min(usersPerAgent, remaining));
                }
            }
            if (xrrSettings.containsKey("execsPerHour")) {
                int execs = xrrSettings.getInteger("execsPerHour");
                int execsPerAgent = (int) Math.ceil((1.0 * execs) / totalAgents);
                int remaining = execs - (agentIndex * execsPerAgent);
                if (remaining <= 0) {
                	xrrSettings.add("execsPerHour", 0);
                } else {
                	xrrSettings.add("execsPerHour", Math.min(execsPerAgent, remaining));
                }
            }
        }
    }
	/*****************************************************************
	 * 
	 *****************************************************************/
    private Thread createUserThread(long pacingSec) {
        long pacingMillis = pacingSec * 1000L;
        PFRUsecase usecase = PFRUsecase.getUsecaseInstance(usecaseClass);
        usecase.initializeUser();
        
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !isTerminated) {
                long start = System.currentTimeMillis();
                try {
                    usecase.execute();
                    HSR.endAllOpen(HSRRecordStatus.Success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable e) {
                    HSR.addException(e);
                    logger.error("Unhandled exception in use case execution", e);
                    HSR.endAllOpen(HSRRecordStatus.Failed);
                } finally {
                    PFRContext.logDetailsClear();
                }

                long duration = System.currentTimeMillis() - start;
                if (pacingMillis > 0 && duration > pacingMillis) {
                    HSR.addWarnMessage("Iteration duration (" + duration + "ms) exceeded pacing (" + pacingMillis + "ms).");
                }

                if (pacingMillis > 0) {
                    long sleepTime = pacingMillis - duration;
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        });
    }

    protected int getCurrentTaskCount() {
        return scheduledUserThreadExecutor == null ? 0 : scheduledUserThreadExecutor.getActiveCount();
    }
	/*****************************************************************
	 * 
	 *****************************************************************/
    @Override
    public void terminate() {
        if (!isTerminated) {
            isTerminated = true;
            executeKillAllThread(); // Re-use logic to clear futures and decrease HSR users
            if (scheduledUserThreadExecutor != null) {
                scheduledUserThreadExecutor.shutdownNow();
            }
        }
    }
	
	

}
