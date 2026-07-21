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
 * Executes a use case with a customizable load pattern.
 * 
 * <ul>
 * <li>Define custom patterns by calling methods in a builder chain (rampUp, start, stable, etc.)</li>
 * <li>Supports graceful and immediate shutdowns</li>
 * <li>Adds pacing to the use cases.</li>
 * </ul>
 *
 * Executes a use case with a custom load pattern.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Mythili Duraisami
 * @author Reto Scheiwiller
 *  
 ***************************************************************************/
public class PFRExecCustom extends PFRExec {
	

	private static final String FIELD_GRACEFUL_SEC = "gracefulSeconds";

	private static final String FIELD_STABLE_MILLIS = "stableMillis";

	private static final String FIELD_EXECS_PER_HOUR = "execsPerHour";

	private static final String FIELD_PACING_SECONDS = "pacingSeconds";

	private static final String FIELD_RAMP_INTERVAL = "rampInterval";

	private static final String FIELD_USER_PER_INTERVAL = "userPerInterval";

	private static final String FIELD_NUM_USERS = "numUsers";

	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExecCustom.class.getName());
	
    private final ArrayList<ModificationType> modifications = new ArrayList<>();
    private final ArrayList<XRRecord> modificationSettings = new ArrayList<>();    
	private long offsetSeconds = 0;
	private int percent = 100;
	
	private boolean isCalculated = false;

	protected enum ModificationType { 
	        START
	      , STOP // stop amount of users immediately
	      , RAMPUP
	      , RAMPDOWN
	      , STABLE
	      , KILLALL // kill all users that are currently running
	      };
	      
	
	private boolean isTerminated = false;
	private ScheduledExecutorService scheduledUserThreadExecutor;
	private Class<? extends PFRUsecase> usecaseClass;
	private String usecaseName;
//	private ScheduledFuture<?> future;
	
	// Scheduler used for delayed graceful kills so we don't block the main pattern thread
    private ScheduledExecutorService gracefulScheduler = Executors.newScheduledThreadPool(1);
    
    // Central storage for running user tasks
    private ArrayList<ScheduledFuture<?>> futureList = new ArrayList<>();
    
	/*****************************************************************
	 * Constructor
	 * 
	 * @param usecaseClass 
	 *****************************************************************/
	public PFRExecCustom(Class<? extends PFRUsecase> usecaseClass) {
		this.usecaseClass = usecaseClass;
		PFRUsecase instance = PFRUsecase.getUsecaseInstance(usecaseClass);
		usecaseName = instance.getName();
	}
	
	
	/*****************************************************************
	 * Adds a modification to this executor.
	 * 
	 * @param type total users to add to the test execution.
	 * @param settings users to add per interval.

	 * @return PFRExecCustom
	 *****************************************************************/
	private void addModification(ModificationType type, XRRecord settings) {
		
        this.modifications.add(type);
        this.modificationSettings.add(settings);
    }
	
	/*****************************************************************
	 * Adds a Step to this executor to add the number of users to the 
	 * test execution by ramping them up based on the interval and the 
	 * amount of users to ramp up per interval.
	 * 
	 * @param numUsers total users to add to the test execution.
	 * @param userPerInterval users to add per interval.
	 * @param rampUpInterval interval in seconds.

	 * @return PFRExecCustom
	 *****************************************************************/
	public PFRExecCustom rampUp(int numUsers, int userPerInterval, int rampUpInterval) {
		
		//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(userPerInterval <= 0) {	userPerInterval = 1; }
		if(rampUpInterval < 0) {	rampUpInterval = 0; }
		
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_USER_PER_INTERVAL, userPerInterval);
        settings.add(FIELD_RAMP_INTERVAL, rampUpInterval);
        settings.add(FIELD_PACING_SECONDS, 0); // No pacing
        
        this.addModification(ModificationType.RAMPUP, settings);
        return this;
    }

	
	/*****************************************************************
	 * Adds a Step to this executor to add the number of users to the 
	 * test execution by ramping them up based on the amount of users 
	 * to ramp up and pacing seconds.
	 * 
	 * The ramp up interval is calculated based on the pacingSeconds.
	 * 
	 * @param numUsers total users to add to the test execution.
	 * @param userPerInterval users to add per interval.
	 * @param pacingSeconds pacing of the users.

	 * @return PFRExecCustom
	 *****************************************************************/
	public PFRExecCustom rampUpPaced(int numUsers, int userPerInterval, int pacingSeconds) {
        
		//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(userPerInterval <= 0) {	userPerInterval = 1; }
		if(pacingSeconds < 0) {	pacingSeconds = 0; }
		
		
		//---------------------------
		// Create Settings
		
		// Calculate ramp up interval internally to fit the pacing
        int rampUpInterval = (int) Math.ceil((1.0 * pacingSeconds / numUsers) * userPerInterval);
        
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_USER_PER_INTERVAL, userPerInterval);
        settings.add(FIELD_PACING_SECONDS, pacingSeconds);
        settings.add(FIELD_RAMP_INTERVAL, rampUpInterval);
        
        this.addModification(ModificationType.RAMPUP, settings);
        return this;
    }	
	
	/*****************************************************************
	 * Adds a Step to this executor to add the number of users to the 
	 * test execution by ramping them up based on the amount of users 
	 * to ramp up and the targeted executions per hour.
	 * 
	 * The ramp up interval and pacing is calculated based on the target
	 * execsPerHour.
	 * 
	 * @param numUsers total users to add to the test execution.
	 * @param userPerInterval users to add per interval.
	 * @param execsPerHour pacing of the userstarget executions per hour.
	 * 
	 * @return PFRExecCustom
	 *****************************************************************/
    public PFRExecCustom rampUpExec(int numUsers, int userPerInterval, int execsPerHour) {
		
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(userPerInterval <= 0) {	userPerInterval = 1; }
		if(execsPerHour < 0) {	execsPerHour = 0; }

		//---------------------------
		// Create Settings
		
    	// Calculate pacing and ramp up
        int pacingSeconds = (int) Math.ceil(3600.0 / ((1.0 * execsPerHour) / numUsers));
        int rampUpInterval = (int) Math.ceil((1.0 * pacingSeconds / numUsers) * userPerInterval);
        
        XRRecord settings = new XRRecord();
        
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_USER_PER_INTERVAL, userPerInterval);
        settings.add(FIELD_EXECS_PER_HOUR, execsPerHour);
        settings.add(FIELD_PACING_SECONDS, pacingSeconds);
        settings.add(FIELD_RAMP_INTERVAL, rampUpInterval);
        
        this.addModification(ModificationType.RAMPUP, settings);
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to immediately add the number of users
     * to the test execution without ramp up nor pacing. 
     *  
     * @param numUsers number of users to start.
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom start(int numUsers) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_PACING_SECONDS, 0);
        
        this.addModification(ModificationType.START, settings);
        
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to immediately add the number of users
     * to the test execution with a target number of executions per hour
     * and without ramp up. 
     *  
     * @param numUsers number of users to start.
     * @param execsPerHour target exections per hour.
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom start(int numUsers, int execsPerHour) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(execsPerHour < 0) {	execsPerHour = 0; }
		
		//---------------------------
		// Create Settings
    	int pacingSeconds = 3600 / (execsPerHour / numUsers);
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_EXECS_PER_HOUR, execsPerHour);
        settings.add(FIELD_PACING_SECONDS, pacingSeconds);
        
        this.addModification(ModificationType.START, settings);
        
        return this;
    }

    /*****************************************************************
     * Adds a step to this executor to keep the load stable for the
     * defined amount of time.
     *
     * @param seconds amount of seconds to keep the load stable
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom stable(int seconds) {
    	//---------------------------
		// Ensure Reasonable Inputs
		if(seconds < 0) {	seconds = 0; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_STABLE_MILLIS, seconds * 1000);
        
        this.addModification(ModificationType.STABLE, settings);
        
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to keep the load stable for the
     * defined amount of time.
     *
     * @param duration amount of time to keep the load stable
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom stable(Duration duration) {
    	
		//---------------------------
		// Create Settings
    	XRRecord settings = new XRRecord();
    	settings.add(FIELD_STABLE_MILLIS, duration.getMilliseconds());
    	
    	this.addModification(ModificationType.STABLE, settings);
    	
    	return this;
    }
    /*****************************************************************
     * Adds a step to this executor to stop users gradually and gracefully.
     *
     * @param numUsers total users to stop.
     * @param userPerInterval users to stop per interval.
     * @param gracefulSeconds time in seconds to allow the user to finish it's current iteration.

     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom rampDownGracefully(int numUsers, int userPerInterval, long gracefulSeconds) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(userPerInterval <= 0) {	userPerInterval = 1; }
		if(gracefulSeconds < 0) {	gracefulSeconds = 0; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_USER_PER_INTERVAL, userPerInterval);
        settings.add(FIELD_GRACEFUL_SEC, gracefulSeconds);

        this.addModification(ModificationType.RAMPDOWN, settings);
        
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to stop users gradually without
     * any graceful time(immediate stop of running iterations).
     *
     * @param numUsers total users to stop.
     * @param userPerInterval users to stop per interval.
     * @param rampDownInterval  interval in seconds.
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom rampDown(int numUsers, int userPerInterval, int rampDownInterval) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(userPerInterval <= 0) {	userPerInterval = 1; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_USER_PER_INTERVAL, userPerInterval);
        settings.add(FIELD_RAMP_INTERVAL, rampDownInterval);
        
        this.addModification(ModificationType.RAMPDOWN, settings);
        
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to stop the amount of users at the
     * same time with a graceful period().
     * 
     * @param numUsers total users to stop.
     * @param gracefulSeconds time in seconds to allow the user to finish it's current iteration.
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom stopGracefully(int numUsers, long gracefulSeconds) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		if(gracefulSeconds < 0) {	gracefulSeconds = 0; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);
        settings.add(FIELD_GRACEFUL_SEC, gracefulSeconds);

        this.addModification(ModificationType.STOP, settings);
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to stop the amount of users at 
     * the same time and immediately (no graceful period).
     * 
     * @param numUsers total users to stop.
     * 
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom stop(int numUsers) {
    	
    	//---------------------------
		// Ensure Reasonable Inputs
		if(numUsers < 0) {	numUsers = 0; }
		
		//---------------------------
		// Create Settings
        XRRecord settings = new XRRecord();
        settings.add(FIELD_NUM_USERS, numUsers);

        this.addModification(ModificationType.STOP, settings);
        return this;
    }
    
    /*****************************************************************
     * Adds a step to this executor to kill any user threads that 
     * might still be running.
     *
     * @return PFRExecCustom
     *****************************************************************/
    public PFRExecCustom killAll() {

    	this.addModification(ModificationType.KILLALL, new XRRecord()); // empty settings
    	
        return this;
    }
    
    /*****************************************************************
     * Execute the threads of the executor.
     * 
     * @return
     * @return PFRExecCustom
     *****************************************************************/
	public void executeThreads() {
		
		//------------------------------
		// Handle Start Offset 
		if (offsetSeconds > 0) {
            try {
                Thread.sleep(offsetSeconds * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
		//------------------------------
		// Execute by Modifications
        scheduledUserThreadExecutor = getScheduledUserExecutor(0);

        try {
            Iterator<ModificationType> typeIter = modifications.iterator();
            Iterator<XRRecord> settingsIter = modificationSettings.iterator();

            while (typeIter.hasNext() && !gracefulStopRequested && !isTerminated) {
                ModificationType modification = typeIter.next();
                XRRecord settings = settingsIter.next();

                switch (modification) {
                    case START:	 		doModificationStart(settings);		break;
                    case RAMPUP:		doModificationRampUp(settings); 	break;
                    case STABLE:		doModificationStable(settings);		break;
                    case RAMPDOWN:		doModificationRampDown(settings);	break;
                    case STOP:			doModificationStop(settings);		break;
                    case KILLALL:		doModificationKillAll();				break;
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
            Thread.currentThread().interrupt();
            return; 
        }finally {
            gracefulScheduler.shutdownNow();
        }	
	}
	
	/*****************************************************************
	 * Starts users threads based on the RAMPUP modification type.
	 *
	 * @param settings the settings for the modification type.
	 * 
	 *****************************************************************/
	private void doModificationRampUp(XRRecord settings) {
		
		//--------------------------------
        // Get Settings
	    int numUsers = settings.getInteger(FIELD_NUM_USERS);
	    int userPerInterval = settings.getInteger(FIELD_USER_PER_INTERVAL);
	    int rampUpInterval = settings.getInteger(FIELD_RAMP_INTERVAL);
	    int pacingSeconds = settings.containsKey(FIELD_PACING_SECONDS) ? settings.getInteger(FIELD_PACING_SECONDS) : 0;
	    
	    int finalPacingMillis = (pacingSeconds > 0) ? pacingSeconds * 1000 : 1;
	    
	    //--------------------------------
        // Do Ramp Up
	    for(int i = 0; i < numUsers && !gracefulStopRequested ; i++) {
	        try {
	            Runnable userThread = createDefaultUserRunnable(usecaseClass, getCurrentUserCount(), pacingSeconds);
	            //userThread.setName(this.getExecutedName() + "-User-" + getCurrentUserCount());
	            
	            // Submit to executor and track future
	            ScheduledFuture<?> future = (ScheduledFuture<?>) scheduledUserThreadExecutor.scheduleAtFixedRate(
	            		  userThread
						, 0
						, finalPacingMillis
						, TimeUnit.MILLISECONDS
					);
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
	            //HSR.addException(e);
	            logger.warn(this.getExecutedName()+": Error While starting User Thread.", e);
	        }
	    }
	}


	/*****************************************************************
	 * Starts users threads based on the START modification type.
	 *
	 * @param settings the settings for the modification type.
	 * 
	 *****************************************************************/
	private void doModificationStart(XRRecord settings) {
		
		//--------------------------------
        // Get Settings
	    int numUsers = settings.getInteger(FIELD_NUM_USERS);
	    
	    int pacingSeconds = 0;
	    if(settings.containsKey(FIELD_PACING_SECONDS)) {
	    	pacingSeconds = settings.getInteger(FIELD_PACING_SECONDS);
	    }
	    
	    int finalPacingMillis = (pacingSeconds > 0) ? pacingSeconds * 1000 : 1;
	    
		//--------------------------------
        // Start Users
	    for (int i = 0; i < numUsers && !gracefulStopRequested; i++) {
	        try {
	        	Runnable userThread = createDefaultUserRunnable(usecaseClass, getCurrentUserCount(), pacingSeconds); 
	            //userThread.setName(this.getExecutedName() + "-User-" + getCurrentUserCount());
	            
	            ScheduledFuture<?> future = scheduledUserThreadExecutor.scheduleAtFixedRate(
	            		  userThread
						, 0
						, finalPacingMillis // can't be zero, therefore using milliseconds
						, TimeUnit.MILLISECONDS
					);
	            
	            futureList.add(future);
	            HSR.increaseUsers(1);
	            
	        } catch (Exception e) {
	            HSR.addException(e);
	            logger.warn(this.getExecutedName() + ": Error while starting user thread.");
	        }
	    }
	}
	
	/*****************************************************************
	 * Handles the STABLE modification type.
	 *
	 * @param settings the settings for the modification type.
	 * 
	 *****************************************************************/
    private void doModificationStable(XRRecord settings) {
    	
    	//--------------------------------
        // Get Settings
        long millis = settings.containsKey(FIELD_STABLE_MILLIS) ? settings.getLong(FIELD_STABLE_MILLIS) : 0; 

        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
	/*****************************************************************
	 * Stops users threads based on the RAMPDOWM modification type.
	 *
	 * @param settings the settings for the modification type.
	 * 
	 *****************************************************************/
    private void doModificationRampDown(XRRecord settings) {
    	
    	//--------------------------------
        // Get Settings
        int numUsers = settings.getInteger(FIELD_NUM_USERS);
        int userPerInterval = settings.getInteger(FIELD_USER_PER_INTERVAL);
        int rampDownInterval = settings.getInteger(FIELD_RAMP_INTERVAL);
        long gracefulMillis = settings.containsKey(FIELD_GRACEFUL_SEC) ? settings.getLong(FIELD_GRACEFUL_SEC) * 1000L : 0;
        
        //--------------------------------
        // Ramp Down
        int stopped = 0;
        while (stopped < numUsers && !futureList.isEmpty() && !gracefulStopRequested) {
            
        	int batchSize = Math.min(userPerInterval, numUsers - stopped);
        	
            for (int i = 0; i < batchSize && !futureList.isEmpty(); i++) {
                ScheduledFuture<?> future = futureList.remove(futureList.size() - 1);
                
                if (gracefulMillis > 0) {
                    // Cancel without interrupting immediately
                    future.cancel(false);
                    // Schedule a hard kill after the graceful time
                    gracefulScheduler.schedule(() -> {
                        if(!future.isDone()) { future.cancel(true); }
                        HSR.decreaseUsers(1);
                    }, gracefulMillis, TimeUnit.MILLISECONDS);
                } else {
                    // Immediate stop
                    future.cancel(true);
                    HSR.decreaseUsers(1);
                }
                stopped++;
            }
            
            // Wait between batches if we haven't stopped enough users yet
            if (stopped < numUsers && rampDownInterval > 0) {
                try {
                    Thread.sleep(rampDownInterval * 1000); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
	/*****************************************************************
	 * Stops users threads based on the STOP modification type.
	 *
	 * @param settings the settings for the modification type.
	 * 
	 *****************************************************************/
    private void doModificationStop(XRRecord settings) {
    	
    	//--------------------------------
        // Get Settings
    	int numUsers = settings.getInteger(FIELD_NUM_USERS); 
        long gracefulMillis = settings.containsKey(FIELD_GRACEFUL_SEC) ? settings.getLong(FIELD_GRACEFUL_SEC) * 1000L : 0;
        
        //--------------------------
        // Request Stop
        ArrayList<ScheduledFuture<?>> usersBeingCancelled = new ArrayList<>();
        
        for (int i = 0; i < numUsers && !futureList.isEmpty(); i++) {
        	ScheduledFuture<?> future = futureList.remove(futureList.size() - 1);
            usersBeingCancelled.add(future);
 
            if (gracefulMillis == 0) {
                future.cancel(true); // Immediate interrupt
                HSR.decreaseUsers(1);
            } else {
                future.cancel(false); // Let it finish gracefully
            }
        }
 
        //--------------------------
        // Force Stop after Graceful Period
        if (gracefulMillis > 0) {
            gracefulScheduler.schedule(() -> {
                for(ScheduledFuture<?> user : usersBeingCancelled){
                    if(!user.isDone()){ 
                        user.cancel(true); // Force kill
                    }
                    HSR.decreaseUsers(1); // Decrease stats when forcefully killed
                }
            }, gracefulMillis, TimeUnit.MILLISECONDS);
        }
    }
	


	/*****************************************************************
	 * Stops users threads based on the KILLALL modification type.
	 * 
	 *****************************************************************/
	public void doModificationKillAll() {
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
	public void calculateLoadSettings() {
		
		if(!isCalculated) {
			
	        if(percent != 100) {
	            for(XRRecord settings : modificationSettings) {
	            	
	            	//-----------------------------
	            	// Calculate Users
	                if (settings.containsKey(FIELD_NUM_USERS)) {
	                    int users = settings.getInteger(FIELD_NUM_USERS);
	                    settings.add(FIELD_NUM_USERS, (int)Math.ceil(users * (percent / 100.0f)));
	                }
	                
	                //-----------------------------
	            	// Calculate Executions Per Hour
	                // TODO: Will not work as pacing is calculated based on Exec when settins are registered.
	                if (settings.containsKey(FIELD_EXECS_PER_HOUR)) {
	                    int execs = settings.getInteger(FIELD_EXECS_PER_HOUR);
	                    settings.add(FIELD_EXECS_PER_HOUR, (int)Math.ceil(execs * (percent / 100.0f)));
	                }
	            }
	        }
		}
	}
	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Override
	public void initialize() {
		
		HSR.setUsecase(usecaseName);
        
        // -----------------------------------------------
        // Apply Percentage to Settings
        // -----------------------------------------------
		calculateLoadSettings();

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
	
	/*****************************************************************
	 * 
	 *****************************************************************/
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
		
		//----------------------------------
		// Calculate Load Parameters
		// ---------------------------------
		calculateLoadSettings();
		
        // Simple distribution: divide user counts proportionally.
        if (totalAgents <= 1) return;

        for (XRRecord settings : modificationSettings) {
        	
        	//-----------------------
        	// Calculate Users
            if (settings.containsKey(FIELD_NUM_USERS)) {
                int users = settings.getInteger(FIELD_NUM_USERS);
                int usersPerAgent = (int) Math.ceil((1.0 * users) / totalAgents);
                int remaining = users - (agentIndex * usersPerAgent);
                if (remaining <= 0) {
                	settings.add(FIELD_NUM_USERS, 0);
                } else {
                	settings.add(FIELD_NUM_USERS, Math.min(usersPerAgent, remaining));
                }
            }
            
            //-----------------------
        	// Calculate Exec Per Hour
            // TODO: Will not work as pacing is calculated based on Exec when settins are registered
            if (settings.containsKey(FIELD_EXECS_PER_HOUR)) {
                int execs = settings.getInteger(FIELD_EXECS_PER_HOUR);
                int execsPerAgent = (int) Math.ceil((1.0 * execs) / totalAgents);
                int remaining = execs - (agentIndex * execsPerAgent);
                if (remaining <= 0) {
                	settings.add(FIELD_EXECS_PER_HOUR, 0);
                } else {
                	settings.add(FIELD_EXECS_PER_HOUR, Math.min(execsPerAgent, remaining));
                }
            }
        }
    }
	

	/*****************************************************************
	 * 
	 *****************************************************************/
    @Override
    public void terminate() {
        if (!isTerminated) {
            isTerminated = true;
            doModificationKillAll(); // Re-use logic to clear futures and decrease HSR users
            if (scheduledUserThreadExecutor != null) {
                scheduledUserThreadExecutor.shutdownNow();
            }
        }
    }
	
	

}
