package com.performetriks.performator.base;

import java.time.Duration;
import java.util.ArrayList;

import com.performetriks.performator.executors.PFRExec;

/***************************************************************************
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
	 * 
	 * @param usecase
	 * @param executor
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest() {
		PFRContext.test(this);
	}
	
	
	/*****************************************************************
	 * This method will return the name of the test.
	 * 
	 *****************************************************************/
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	/***************************************************************************
	 * 
	 * @param usecase
	 * @param executor
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest add(PFRExec executor ){
		executorList.add(executor);
		executor.test(this);
		return this;
	}
	
	
	/***************************************************************************
	 * returns a clone of the executors that have been added to this test.
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
	 * @param maxDuration
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRTest maxDuration(Duration maxDuration){
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
	public PFRTest gracefulStop(Duration gracefulStop){
		this.gracefulStop = gracefulStop;
		return this;
	}
	
	/***************************************************************************
	 * 
	 * @return duration
	 ***************************************************************************/
	public Duration gracefulStop(){
		return gracefulStop;
	}
	
	
}
