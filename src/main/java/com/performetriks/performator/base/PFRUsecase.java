package com.performetriks.performator.base;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public interface PFRUsecase {

	/*****************************************************************
	 * This method will be executed once when the test starts.
	 * 
	 *****************************************************************/
	public void initialize(PFRContext context);
	
	/*****************************************************************
	 * This method will contain the main steps of your use case
	 * 
	 *****************************************************************/
	public void execute(PFRContext context);
	
	/*****************************************************************
	 * This method will be executed once when the test has finished.
	 * 
	 *****************************************************************/
	public void terminate(PFRContext context);
	
	
}
