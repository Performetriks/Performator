package com.performetriks.performator.distribute;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

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
	 * @throws URISyntaxException 
	 **********************************************************************************/
	public static URI getJarFileURIForTest(PFRTest test) throws URISyntaxException {
		
		return test.getClass()
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI();
	}

	/**********************************************************************************
	 * Sends 
	 **********************************************************************************/
	public void sendJar(CountDownLatch latch) {

		ZePFRClient instance = this;

	    try {
	    	File jarFile = new File(
	    			getJarFileURIForTest(test)
	        );
	    	
			byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
			
			new RemoteRequest(instance, Command.transferjar, test)
				.param(PARAM_BODY_LENGTH, ""+jarBytes.length)
				.body(jarBytes)
				.sendAsync(latch)
			;
			
		}catch (Exception e) {
	        logger.error("Issue while loading and transferring jar-file to remote agent.", e);
	    }
			

	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse getStatus(){
		
		return new RemoteRequest(this, Command.status, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse reserveAgent(){
		
		return new RemoteRequest(this, Command.reserve, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse testStart(){
		return new RemoteRequest(this, Command.teststart, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse testStatus(){
		return new RemoteRequest(this, Command.teststatus, test).send();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public RemoteResponse testStop(){
		return new RemoteRequest(this, Command.teststop, test).send();
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
