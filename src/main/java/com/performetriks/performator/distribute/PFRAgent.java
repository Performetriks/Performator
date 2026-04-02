package com.performetriks.performator.distribute;

import java.util.HashSet;

import com.performetriks.performator.base.PFRConfig;

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
	
	private String hostname;
	private int port;
	private boolean isActive = true;
	private HashSet<String> tags = new HashSet<>();
	
	// progress in percent of test jar file uploaded
	private int uploadProgressPercent = 0;
	
	
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
	 * @return port
	 *************************************************************/
	public int port() {
		return port;
	}
	
	/*************************************************************
	 * @return active
	 *************************************************************/
	public boolean active() {
		return isActive;
	}
	
	/*************************************************************
	 * Define if the agent is active or not. Easy way to
	 * disable an agent.
	 * 
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent active(boolean active) {
		
		this.isActive = active;
		
		return this;
	}
	
	/*************************************************************
	 * Returns true if the agent has the specified tag.
	 * 
	 * @return instance for chaining
	 *************************************************************/
	public boolean hasTag(String tag) {
		
		return tags.contains(tag);
	}
	
	/*************************************************************
	 * Adds one or more tags to this agent definition.
	 * 
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent tag(String... agentTags) {
		
		for(String tag : agentTags) {
			this.tags.add(tag);
		}
		return this;
	}
	
	/*************************************************************
	 * Returns the upload progress of the JAR file upload.
	 * 
	 * @return uploadProgressPercent
	 *************************************************************/
	@Override
	public String toString() {
		return hostname+":"+port;
	}
	
	/*************************************************************
	 * Returns the upload progress of the JAR file upload.
	 * 
	 * @return uploadProgressPercent
	 *************************************************************/
	public int uploadProgressPercent() {
		return uploadProgressPercent;
	}
	
	/*************************************************************
	 * INTERNAL USE: Set the upload progress of the JAR file upload.
	 *************************************************************/
	protected void uploadProgressPercent(int percent) {
		uploadProgressPercent = percent;
	}

}
