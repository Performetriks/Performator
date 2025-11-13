package com.performetriks.performator.executors;

import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRExec {
	
	private static Logger logger = (Logger) LoggerFactory.getLogger(PFRExec.class.getName());
	
	private PFRTest test;
	private Class<? extends PFRUsecase> usecaseClass;
	private String usecaseName;
	
	/*****************************************************************
	 * Constructor
	 * 
	 *****************************************************************/
	public PFRExec(Class<? extends PFRUsecase> usecaseClass){
		this.usecaseClass = usecaseClass;
		this.usecaseName = this.getUsecaseInstance().getName();
	}
	
	/*****************************************************************
	 * 
	 * @return usecase
	 *****************************************************************/
	public PFRUsecase getUsecaseInstance(){
		
		try {
			return (PFRUsecase) usecaseClass.getDeclaredConstructor()
		    		.newInstance();

		} catch (Exception e) {
			logger.error("Error while creating instance for class "+usecaseClass.getName(), e);
		}
		
		return null;
		
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public PFRExec test(PFRTest test){
		this.test = test;
		return this;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public PFRTest test(){
		return test;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public String usecaseName(){
		return usecaseName;
	}
	
	/*****************************************************************
	 * Add values to the parameter passed to this method, with key-value 
	 * pairs that give details about the settings of this executor.
	 * 
	 * This will be executed before distributeLoad() and initialize().
	 * If do any calculations in initialize() for the load, it is 
	 * recommended to do these calculations in a separate method.
	 * 
	 * @param JsonObject object to add settings to
	 *****************************************************************/
	public abstract void getSettings(JsonObject object);	
	
	/*****************************************************************
	 * This method will be executed by agents to calculate the amount
	 * of load that a specific agent will execute.
	 * It will be executed before initialize() is called.
	 * 
	 * The agentNumber is 1 for the first 2 for the second etc... and
	 * can be used to calculate the load a specific agent will hold.
	 * 
	 * @param totalAgent agent count
	 * @param agentIndex the number of the agent
	 * @param recursionIndex can be used to call this method recursively.
	 * 
	 *****************************************************************/
	public abstract void distributeLoad(int totalAgents, int agentIndex, int recursionIndex);
	
	/*****************************************************************
	 * Method to do any kind of initialization before executeUsecase()
	 * is called.
	 *****************************************************************/
	public abstract void initialize();	
	
	
	/*****************************************************************
	 * This method will start and manage the users of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public abstract void executeThreads();
	
	/*****************************************************************
	 * stop Gracefully
	 *****************************************************************/
	public abstract void gracefulStop();	
	
	/*****************************************************************
	 * Method to do any kind of termination before executeUsecase()
	 * is called.
	 *****************************************************************/
	public abstract void terminate();
	
	
	/*****************************************************************
	 * This method will start and manage the threads of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public void execute() {
		
		HSR.setUsecase(usecaseName);
		
		initialize();
		
		executeThreads();
		
		terminate();
	}

	
}
