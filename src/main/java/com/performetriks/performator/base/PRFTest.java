package com.performetriks.performator.base;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PRFTest {
	
	
	private ArrayList<PFRExecutor> executorList = new ArrayList<>();
	
	private Duration maxDuration = Duration.of(1, ChronoUnit.HOURS);
	
	/***************************************************************************
	 * 
	 * @param usecase
	 * @param executor
	 * @return instance for chaining
	 ***************************************************************************/
	public PRFTest(PFRContext context) {
		
	}
	
	/***************************************************************************
	 * 
	 * @param usecase
	 * @param executor
	 * @return instance for chaining
	 ***************************************************************************/
	public PRFTest add(PFRExecutor executor ){
		executorList.add(executor);
		return this;
	}
	
	/***************************************************************************
	 * Sets the maximum duration of the test.
	 * @param maxDuration
	 * @return instance for chaining
	 ***************************************************************************/
	public PRFTest maxDuration(Duration maxDuration){
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
	
	
}
