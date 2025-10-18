package com.performetriks.performator.executors;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRExecutor {
	
	
	private PFRTest test;
	private PFRUsecase usecase;
	
	/*****************************************************************
	 * Constructor
	 * 
	 *****************************************************************/
	public PFRExecutor(PFRUsecase usecase){
		this.usecase = usecase;
	}
	
	/*****************************************************************
	 * 
	 * @return usecase
	 *****************************************************************/
	public PFRUsecase usecase(){
		return usecase;
	}
	
	/*****************************************************************
	 * 
	 * @return test
	 *****************************************************************/
	public PFRExecutor test(PFRTest test){
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
	public abstract void initialize(PFRContext context);	
	
	/*****************************************************************
	 * Return a JsonObject with key-value pairs that give details
	 * about the load pattern.
	 * This will be executed after initialize() and before 
	 * executeUsecase().
	 * 
	 * @return JsonObject
	 *****************************************************************/
	public abstract JsonObject getSettings(PFRContext context);	
	
	/*****************************************************************
	 * This method will start and manage the users of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public abstract void executeThreads(PFRContext context);
	
	/*****************************************************************
	 * stop Gracefully
	 *****************************************************************/
	public abstract void gracefulStop();	
	
	/*****************************************************************
	 * Method to do any kind of termination before executeUsecase()
	 * is called.
	 *****************************************************************/
	public abstract void terminate(PFRContext context);	
	
	
	/*****************************************************************
	 * This method will start and manage the threads of the executor.
	 * Implement it to handle any kind of runtime exceptions.
	 *****************************************************************/
	public void execute(PFRContext context) {
		
		HSR.setUsecase(usecase.getName());
		
		initialize(context);
		
		executeThreads(context);
		
		terminate(context);
	}

	
}
