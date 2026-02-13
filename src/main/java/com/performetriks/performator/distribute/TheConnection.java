package com.performetriks.performator.distribute;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.distribute.TheServer.Command;

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
public class TheConnection {
	
	private static final Logger logger = LoggerFactory.getLogger(TheConnection.class);
	
	static final String PARAM_BODY_LENGTH = "internal-body-length";
	static final String PARAM_HOST = "internal-host";
	static final String PARAM_PORT = "internal-port";
	
	private String remoteHost;
	private int remotePort;
	private PFRTest test;
	
	/**********************************************************************************
	 * Connects this instance to a agent or collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public TheConnection(PFRAgent agent, PFRTest test){
		this.remoteHost = agent.hostname();
		this.remotePort = agent.port();
		this.test = test;
	}
	
	
	/**********************************************************************************
	 * Connects this instance to a agent or collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public TheConnection(String remoteHost, int remotePort) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse sendJar(File jarFile) throws IOException {

		byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
		
		return new RemoteRequest(this, Command.SEND_JAR, test)
					.body(jarBytes)
					.send()
					;
		
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse getStatus(){
		
		return new RemoteRequest(this, Command.GET_STATUS, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse reserveAgent(){
		
		return new RemoteRequest(this, Command.RESERVE_AGENT, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse stop(){
		
		return new RemoteRequest(this, Command.STOP, test).send();
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
