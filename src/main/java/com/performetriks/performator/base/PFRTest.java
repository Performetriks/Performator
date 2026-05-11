package com.performetriks.performator.base;

import java.time.Duration;
import java.util.ArrayList;

import com.performetriks.performator.executors.PFRExec;

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
	
	private ArrayList<PFRExec> executorList = new ArrayList<>();
	
	private Duration maxDuration = Duration.ofHours(1);
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
	 * 
	 * @param maxDuration
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest maxDuration(Duration maxDuration){
		this.maxDuration = maxDuration;
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
	
	
}
