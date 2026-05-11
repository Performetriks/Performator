package com.performetriks.performator.distribute;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.performetriks.performator.base.PFR;

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
	 * Creates an agent pool containing the given agents.
	 * 
	 * @param agents the agents to add to this pool
	 *************************************************************/
	public PFRAgentPool(PFRAgent... agents) {
		
		for(PFRAgent agent : agents) {
			agentList.add(agent);
		}
		
	}
	
	/*************************************************************
	 * Creates an agent pool by loading them from the specified
	 * json file.
	 * 
	 * JSON Structure:
	 * <pre><code>[
  {
    "host": "deactivatedAgent",
    "port": 1234,
    "active": false,
    "tags": ["cloud","windows"]
  },
  {
    "host": "winserver123",
    "port": 1234,
    "active": true,
    "tags": ["cloud","windows"]
  }, ... </code></pre>
	 * 
	 * @param packageName the name of the java package like "com.mycompany.data"
	 * @param jsonFilename the file to be loaded
	 *************************************************************/
	public PFRAgentPool(String packageName, String jsonFilename) {
		
		String json = PFR.Files.readPackageResource(packageName, jsonFilename);
		
		if( json != null && json.trim().startsWith("")) {
			JsonArray array = PFR.JSON.fromJson(json).getAsJsonArray();
			loadAgentsFromJson(array);
		}
		
	}
	
	/*************************************************************
	 * Creates an agent pool by loading them from the specified
	 * json file.
	 * 
	 * JSON Structure:
	 * <pre><code>[
  {
    "host": "deactivatedAgent",
    "port": 1234,
    "active": false,
    "tags": ["cloud","windows"]
  },
  {
    "host": "winserver123",
    "port": 1234,
    "active": true,
    "tags": ["cloud","windows"]
  }, ... </code></pre>
	 * 
	 * @param array that should be loaded
	 *************************************************************/
	public PFRAgentPool(JsonArray array) {
		loadAgentsFromJson(array);
	}
	
	/*************************************************************
	 * Loads the agents from the given Json array.
	 * 
	 * JSON Structure:
	 * <pre><code>[
  {
    "host": "deactivatedAgent",
    "port": 1234,
    "active": false,
    "tags": ["cloud","windows"]
  },
  {
    "host": "winserver123",
    "port": 1234,
    "active": true,
    "tags": ["cloud","windows"]
  }, ... </code></pre>
     * 
     * @param array that should be loaded
	 *************************************************************/
	public void loadAgentsFromJson(JsonArray array){
		
		for(JsonElement e : array) {
			if(e.isJsonObject()) {
				this.add(
					new PFRAgent(e.getAsJsonObject())
				);
			}
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
	
	
	/*************************************************************
	 * Returns a JsonArray represenation of this pool.
	 * 
	 * @return array
	 *************************************************************/
	public JsonArray toJson(){
		JsonArray array = new JsonArray();
		
		for(PFRAgent agent : agentList) {
			array.add(agent.toJson());
		}
		
		return array;
	}
	
	
}
