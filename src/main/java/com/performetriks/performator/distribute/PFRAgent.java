package com.performetriks.performator.distribute;

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
public class PFRAgent {
	
	public String hostname;
	public int port;
	public HashSet<String> tags = new HashSet<>();
	
	/*************************************************************
	 * Start the instance and run the test either locally or remote
	 * on agents if agents are defined.
	 * 
	 * @param hostname the name of the host, fully qualified name is recommended, e.g. winabc123.acme.com
	 * @param port the port the agent was started with. 
	 *************************************************************/
	public PFRAgent(String hostname, int port) {
		this.hostname = hostname;
		this.port = port;
	}
	
	/*************************************************************
	 * Start the instance and run the test either locally or remote
	 * on agents if agents are defined.
	 * 
	 * @param hostname the name of the host, fully qualified name is recommended, e.g. winabc123.acme.com
	 * @param port the port the agent was started with. 
	 * @param tags
	 *************************************************************/
	public PFRAgent(String hostname, int port, String... tags) {
		
		this(hostname, port);
		
		for(String tag : tags) {
			this.tags.add(tag);
		}

	}
	
	/*************************************************************
	 * @return hostname
	 *************************************************************/
	public String hostname() {
		return hostname;
	}
	
	/*************************************************************
	 * @return hostname
	 *************************************************************/
	public int port() {
		return port;
	}

}
