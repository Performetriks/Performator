package com.performetriks.performator.base;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRUsecase {

	
	/*****************************************************************
	 * This method will return the name of the usecase.
	 * 
	 *****************************************************************/
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	/*****************************************************************
	 * This method will be executed once when the user starts.
	 * 
	 *****************************************************************/
	public abstract void initializeUser();
	
	/*****************************************************************
	 * This method will contain the main steps of your use case
	 * 
	 *****************************************************************/
	public abstract void execute() throws Throwable;
	
	/*****************************************************************
	 * This method will be executed once when the test has finished.
	 * 
	 *****************************************************************/
	public abstract void terminate();
	
	
}
