package com.performetriks.performator.base;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;

import org.slf4j.Logger;

import com.performetriks.performator.executors.PFRExec;
import org.slf4j.LoggerFactory;

/***************************************************************************
 * The abstract class used to implement Tests for the Performator Framework.
 * A class is used to combine executors(PFRExec*) and usecases (instances of
 * PFRUsecase) as well as initialization and configuration code needed to 
 * setup a test.
 *  
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRTest {
	
	private static Logger logger = LoggerFactory.getLogger(PFRTest.class.getName());
	
	private ArrayList<PFRExec> executorList = new ArrayList<>();
	
	private Duration maxDuration = Duration.ofMinutes(2);
	private Duration gracefulStop = Duration.ofMinutes(1);
	
	/***************************************************************************
	 * Constructor
	 ***************************************************************************/
	public PFRTest() {
		PFRContext.test(this);
	}
	
	
	/*****************************************************************
	 * This method will return the name of the test. By default this 
	 * is the name of the class. You can override this method if you
	 * want to change the name of the test.
	 * 
	 *****************************************************************/
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	/***************************************************************************
	 * Adds an executor to this test.
	 * 
	 * @param executor
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest add(PFRExec executor){
		executorList.add(executor);
		executor.test(this);
		return this;
	}
	
	/***************************************************************************
	 * Clears the list of executors for this test.
	 ***************************************************************************/
	public void clearExecutors(){
		executorList.clear();
	}
	
	/***************************************************************************
	 * Returns a clone of the executors that have been added to this test.
	 * 
	 * @return cloned list of executors
	 ***************************************************************************/
	public ArrayList<PFRExec> getExecutors(){
		ArrayList<PFRExec> clone = new ArrayList<>();
		clone.addAll(executorList);
		return clone;
	}
	
	/***************************************************************************
	 * Sets the maximum duration of the test, default is 1 hour.
	 * If the maxDuration is smaller than 2 minutes, this method will do nothing.
	 * This lower limit is needed as else reporting and agent startups might not
	 * happen in time and will cause missing data for. 
	 * 
	 * @param maxDuration equals or bigger than 2 minutes.
	 * 
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest maxDuration(Duration maxDuration){
		
		if(maxDuration.toMillis() >= 120_000) {
			this.maxDuration = maxDuration;
		}
		return this;
	}
	
	/***************************************************************************
	 * Returns the maximum duration of the test.
	 * @return duration
	 ***************************************************************************/
	public Duration maxDuration(){
		return maxDuration;
	}
	
	/***************************************************************************
	 * Duration for gracefully stopping the test. Default is 1 minute.
	 * This time is added after max duration has been reached to allow currently
	 * active executions to finish their use case steps.
	 * 
	 * @param gracefulStop
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest gracefulStop(Duration gracefulStop){
		this.gracefulStop = gracefulStop;
		return this;
	}
	
	/***************************************************************************
	 * Returns the graceful stop duration.
	 * 
	 * @return duration
	 ***************************************************************************/
	public Duration gracefulStop(){
		return gracefulStop;
	}
	
	
	/*************************************************************
	 * Get an instance of a PFRTest class by name.
	 * 
	 * @param className 
	 * @return instance or null on error.
	 *************************************************************/
	public static PFRTest createTestInstance(String className) {
			
		//----------------------------------
		// Check Null
		if(className == null) {
			logger.info("Please specify the class name of the test");
			return null;
		}
		
		try {

			//----------------------------------
			// Get Class
			Class<?> clazz = Class.forName(className);
		    
			if(! PFRTest.class.isAssignableFrom(clazz) ){
		    	logger.info("The specified test class "+className+" must be a subclass of "+PFRTest.class.getName()+".");
				return null;
		    }
			
			//----------------------------------
			// Create Instance
			return createTestInstance((Class<PFRTest>)clazz);

		    
		} catch (Exception e) {
			logger.error("Error while creating instance for class "+className, e);
		}
		
		return null;

	}
	
	/*************************************************************
	 * Get an instance of a PFRTest class by name.
	 * 
	 * @param className 
	 * @return instance or null on error.
	 *************************************************************/
	public static PFRTest createTestInstance(Class<PFRTest> clazz) {
		
		try {
			PFRTest instance = clazz.getDeclaredConstructor().newInstance();
			return instance;
		} catch (Exception e) {
			logger.error("Error while creating instance for class: "+clazz.getName(), e);
		}
		
	    return null;
		
	}
	
	
}
