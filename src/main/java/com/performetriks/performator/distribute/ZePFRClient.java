package com.performetriks.performator.distribute;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.distribute.ZePFRServer.Command;

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
public class ZePFRClient {
	
	private static final Logger logger = LoggerFactory.getLogger(ZePFRClient.class);
	
	static final String PARAM_BODY_LENGTH = "body-length";
	static final String PARAM_HOST = "host";
	static final String PARAM_PORT = "port";
	static final String PARAM_TEST = "test";
	
	private PFRAgent agent;
	private String remoteHost;
	private int remotePort;
	private PFRTest test;
	
	/**********************************************************************************
	 * Connects this instance to a agent or collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public ZePFRClient(PFRAgent agent, PFRTest test){
		this.agent = agent;
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
	public ZePFRClient(String remoteHost, int remotePort) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	

	/**********************************************************************************
	 * Sends 
	 **********************************************************************************/
	public void sendJar(CountDownLatch latch) {

		ZePFRClient instance = this;

	    try {
	    	File jarFile = new File(
	            test.getClass()
	                .getProtectionDomain()
	                .getCodeSource()
	                .getLocation()
	                .toURI()
	        );
	        System.out.println("JAR File Path: "+jarFile.getAbsolutePath());
			byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
			
			
			new RemoteRequest(instance, Command.TRANSFER_JAR, test)
				.param(PARAM_BODY_LENGTH, ""+jarBytes.length)
				.body(jarBytes)
				.sendAsync(latch)
			;
			
	    } catch (Exception e) {
	        logger.error("Issue while loading and transferring jar-file to remote agent.", e);
	    }
			

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
	 * Returns the agent the client connects too.
	 **********************************************************************************/
	public PFRAgent getAgent() {
		return agent;
	}
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public String getHost() {
		return remoteHost;
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public int getPort(){
		return remotePort;
	}

}
