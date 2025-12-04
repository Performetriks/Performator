package com.performetriks.performator.base;

import org.slf4j.LoggerFactory;

import com.performetriks.performator.executors.PFRExec;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRUsecase {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRUsecase.class.getName());
	
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
	
	/*****************************************************************
	 * This method will return the name of the usecase.
	 * 
	 *****************************************************************/
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	/*****************************************************************
	 * 
	 * @return usecase
	 *****************************************************************/
	public static PFRUsecase getUsecaseInstance(Class<? extends PFRUsecase> usecaseClass){
		
		try {
			return (PFRUsecase) usecaseClass.getDeclaredConstructor()
		    		.newInstance();

		} catch (Exception e) {
			logger.error("Error while creating instance for class "+usecaseClass.getName(), e);
		}
		
		return null;
		
	}
	

	
	
}
