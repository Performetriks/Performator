package com.performetriks.performator.distribute;

import java.util.Collection;
import java.util.HashSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.xresch.xrutils.base.XR;

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
	 * Create a new agent with hostname and port.
	 * 
	 * @param hostname the name of the host, fully qualified name is recommended, e.g. winabc123.acme.com
	 * @param port the port the agent was started with. 
	 *************************************************************/
	public PFRAgent(String hostname, int port) {
		this.hostname = hostname;
		this.port = port;
	}
	
	/*************************************************************
	 * Create a new agent with hostname, port and the given tags.
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
	 * Create an agent from the Json Object. 
	 * 
	 * JSON Structure:
	 * <pre><code>{
    "host": "winserver123",
    "port": 1234,
    "active": true,
    "tags": ["cloud","windows"]
}</code></pre>
	 * 
	 * @param object
	 *************************************************************/
	public PFRAgent(JsonObject object) {
		
		if(object == null) { throw new IllegalArgumentException("Object cannot be null."); }
		
		if(object.has("host")) { hostname = object.get("host").getAsString(); }
		if(object.has("port")) { port = object.get("port").getAsInt(); }
		if(object.has("active")) { isActive = object.get("active").getAsBoolean(); }
		
		if(object.has("tags")) { 
			JsonArray tagsArray = object.get("tags").getAsJsonArray();	
			for(JsonElement e : tagsArray) {
				tags.add(e.getAsString());
			}
		}
			
	}
	
	/*************************************************************
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent setHostname(String hostname) {
		this.hostname = hostname;
		return this;
	}
	
	/*************************************************************
	 * @return hostname
	 *************************************************************/
	public String getHostname() {
		return hostname;
	}
	
	/*************************************************************
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent setPort(int port) {
		this.port = port;
		return this;
	}
	
	/*************************************************************
	 * @return port
	 *************************************************************/
	public int getPort() {
		return port;
	}
	
	/*************************************************************
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent setActive(boolean isActive) {
		this.isActive = isActive;
		return this;
	}
	
	/*************************************************************
	 * @return active
	 *************************************************************/
	public boolean isActive() {
		return isActive;
	}
	
	/*************************************************************
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent setTags(HashSet<String> tags) {
		this.tags = tags;
		return this;
	}
	
	/*************************************************************
	 * @return hostname
	 *************************************************************/
	public HashSet<String> getTags() {
		return tags;
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
	public PFRAgent addTags(String... agentTags) {
		
		if(agentTags == null) { return this; }
		
		for(String tag : agentTags) {
			this.tags.add(tag);
		}
		return this;
	}
	
	/*************************************************************
	 * Adds one or more tags to this agent definition.
	 * 
	 * @return instance for chaining
	 *************************************************************/
	public PFRAgent addTags(Collection<String> agentTags) {
		
		if(agentTags == null) { return this; }
		
		for(String tag : agentTags) {
			this.tags.add(tag);
		}
		return this;
	}
	
	/*************************************************************
	 * 
	 *************************************************************/
	public String toJsonString() {
		return XR.JSON.getGsonInstance().toJson(this);
	}
	
	/*************************************************************
	 * 
	 *************************************************************/
	public JsonElement toJson() {
		return XR.JSON.getGsonInstance().toJsonTree(this);
	}
	
	/*************************************************************
	 * 
	 *************************************************************/
	public static PFRAgent fromJson(String json) {
		return XR.JSON.getGsonInstance().fromJson(json, PFRAgent.class);
	}
	
	/*************************************************************
	 * Returns a string that contains hostname and port of this agent.
	 * 
	 * @return string
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
