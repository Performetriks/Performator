package com.performetriks.performator.base;

import java.time.Duration;

/**************************************************************************************************************
 * Th configuration class for the performator framework.
 * 
 * @author Reto Scheiwiller, (c) Copyright 2025
 * @license EPL-License
 * 
 **************************************************************************************************************/
public class PFRConfig {
	
	private static int instancePort = 9876;
	private static Duration timeoutAgentReset = Duration.ofMinutes(5);
	
	/**********************************************************************************
	 * Set the port of this instance when used as a controller or an agent.
	 **********************************************************************************/
	public static void port(int port) {
		instancePort = port;
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public static int port() {
		return instancePort;
	}
	
	/**********************************************************************************
	 * The amount of time an agent that is executing a test should try to reconnect to 
	 * the controller until it considers the controller terminated.
	 * This is useful to reset agents and not let them be blocked for a long time.
	 **********************************************************************************/
	public static void timeoutAgentReset(Duration timout) {
		instancePort = port;
	}

}
