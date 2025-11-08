package com.performetriks.performator.base;

import java.time.Duration;
import java.util.HashSet;

import com.performetriks.performator.distribute.PFRAgentPool;

/**************************************************************************************************************
 * The configuration class for the performator framework.
 * 
 * @author Reto Scheiwiller, (c) Copyright 2025
 * @license EPL-License
 * 
 **************************************************************************************************************/
public class PFRConfig {
	
	
	private static Mode executionMode = Mode.AUTO;
	
	private static int instancePort = 9876;
	private static Duration timeoutAgentReset = Duration.ofMinutes(5);
	
	private static PFRAgentPool agentPool = null;
	private static int agentAmount = 0;  // 0 or smaller equals is use all agents
	private static String[] agentTags = null;  // filter agents by these tags
	
	/**********************************************************************************
	 * Mode Enum
	 **********************************************************************************/
	public enum Mode {
		  AUTO("Executes either locally, or on agents if agents are defined.")
		, LOCAL("Execute test locally, does not execute anything on agents. Useful for development.")
		, AGENT("Start the performator.jar as an agent.")
		, REMOTE("Mode used by agents to execute a jar file. Remote will remove all HSRReporters and reports data to the controller instead.")
		;
		
		private static HashSet<String> names = new HashSet<>();
		static {
			for(Mode mode : Mode.values()) { names.add(mode.name()); }
		}
		
		private String description;
		
		private Mode(String description) {
			this.description = description;
		}
		
		public String description() { return description; }

		public static boolean has(String value) { return names.contains(value); }
	
	}
	
	/**********************************************************************************
	 * Set the port of this instance when used as a controller or an agent.
	 **********************************************************************************/
	protected static void executionMode(Mode mode) {
		
		if(mode == null) { return; }
		
		executionMode = mode;
		
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static Mode executionMode() {
		return executionMode;
	}
	
	/**********************************************************************************
	 * Set the port of this instance when used as a controller or an agent.
	 **********************************************************************************/
	public static void port(int port) {
		instancePort = port;
	}
	
	/**********************************************************************************
	 * Get the port of this instance.
	 * 
	 * @return port
	 **********************************************************************************/
	public static int port() {
		return instancePort;
	}
	
	/**********************************************************************************
	 * The amount of time an agent that is executing a test should try to reconnect to 
	 * the controller until it considers the controller terminated.
	 * This is useful to reset agents and not let them be blocked for a long time.
	 **********************************************************************************/
	public static void timeoutAgentReset(Duration timeout) {
		timeoutAgentReset = timeout;
	}

	/**********************************************************************************
	 * Checks if any agents have been defined.
	 * 
	 **********************************************************************************/
	public static boolean hasAgents() {
		return ( agentPool != null && agentPool.hasAgents() ) ;
	}
	/**********************************************************************************
	 * Get the port of this instance.
	 * 
	 **********************************************************************************/
	public static PFRAgentPool getAgentPool() {
		return agentPool;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static void setAgentPool(PFRAgentPool agentPool) {
		PFRConfig.agentPool = agentPool;
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static int getAgentAmount() {
		return agentAmount;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static void setAgentAmount(int agentAmount) {
		PFRConfig.agentAmount = agentAmount;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static String[] getAgentTags() {
		return agentTags;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static void setAgentTags(String[] agentTags) {
		PFRConfig.agentTags = agentTags;
	}
	
	

}
