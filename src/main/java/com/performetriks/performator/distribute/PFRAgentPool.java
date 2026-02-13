package com.performetriks.performator.distribute;

import java.util.ArrayList;
import java.util.HashSet;

/**************************************************************************************************************
 * This class is used to define agent connections to run tests remotely.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 **************************************************************************************************************/
public class PFRAgentPool {

	public ArrayList<PFRAgent> agentList = new ArrayList<>();

	/*************************************************************
	 * Start the instance and run the test either locally or remote
	 * on agents if agents are defined.
	 * 
	 * @param agents the agents to add to this pool
	 *************************************************************/
	public PFRAgentPool(PFRAgent... agents) {
		
		for(PFRAgent agent : agents) {
			agentList.add(agent);
		}
		
	}
	

	/*************************************************************
	 * Add an agent to the pool.
	 *************************************************************/
	public boolean add(PFRAgent agent){
		return agentList.add(agent);
	}
	
	/*************************************************************
	 * Checks if this Agent Pool has agents defined
	 *************************************************************/
	public boolean hasAgents(){
		return agentList.size() > 0;
	}
	
	/*************************************************************
	 * Returns the number of agents in the pool
	 *************************************************************/
	public int size(){
		return agentList.size();
	}
	
	/*************************************************************
	 * Returns the element at the index.
	 * 
	 * @param i index of the element
	 *************************************************************/
	public PFRAgent get(int i){
		return agentList.get(i);
	}
	
	
}
