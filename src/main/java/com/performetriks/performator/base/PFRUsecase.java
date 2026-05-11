package com.performetriks.performator.base;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * The abstract class used to implement use cases for the Performator Framework.
 * A use case implements the steps that should be tested. Use cases are combined
 * with executors(PFFRExec*) in tests(PFRTest) to create load tests.
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
	 * This method will return the name of the usecase. By default
	 * returns the class name, can be overridden if you want to
	 * change the name.
	 * 
	 *****************************************************************/
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	/*****************************************************************
	 * Create a new instance for the given use case class.
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
