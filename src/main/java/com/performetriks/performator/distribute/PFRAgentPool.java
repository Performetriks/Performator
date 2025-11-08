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
	 * Checks if this Agent Pool has agents defined
	 *************************************************************/
	public boolean hasAgents(){
		return agentList.size() > 0;
	}
	
	
}
