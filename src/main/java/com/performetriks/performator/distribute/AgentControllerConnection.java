package com.performetriks.performator.distribute;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.performetriks.performator.distribute.AgentControllerServer.Command;

/**************************************************************************************************************
 * This class is used to establish a connection between an agent and a controller or vice versa.
 * It has to be created after a PFRTest instance has been loaded to get let the user set the configuration.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 **************************************************************************************************************/
class AgentControllerConnection {
	
	private static final Logger logger = LoggerFactory.getLogger(AgentControllerConnection.class);
	
	static final String PARAM_BODY_LENGTH = "internal-body-length";
	static final String PARAM_HOST = "internal-host";
	static final String PARAM_PORT = "internal-port";
	
	private String remoteHost;
	private int remotePort;
	
	/**********************************************************************************
	 * Connects this instance to a agent or collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public AgentControllerConnection(PFRAgent agent) throws IOException {
		this.remoteHost = agent.hostname();
		this.remotePort = agent.port();
	}
	
	/**********************************************************************************
	 * Connects this instance to a agent or collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public AgentControllerConnection(String remoteHost, int remotePort) throws IOException {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void sendJar(File jarFile) throws IOException {

		byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
		
		new RemoteRequest(this, Command.SEND_JAR)
					.body(jarBytes)
					.send()
					;
		
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void getStatus() throws IOException {
		
		RemoteResponse response = 
				new RemoteRequest(this, Command.GET_STATUS)
							.send()
							;
		
		if(response.success()) {
			JsonElement object = response.payload();
		}

	}
	
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public String getHost() throws IOException {
		return remoteHost;
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public int getPort() throws IOException {
		return remotePort;
	}
	
	


}
