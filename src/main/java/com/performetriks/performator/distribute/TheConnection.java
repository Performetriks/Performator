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
	
	static final String PARAM_BODY_LENGTH = "body-length";
	static final String PARAM_HOST = "host";
	static final String PARAM_PORT = "port";
	
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
	public void sendJar(CountDownLatch latch) {

		TheConnection instance = this;
		Thread senderThread = new Thread(new Runnable() {
			
				@Override
				public void run() {
				 // Get path of the currently running JAR
			    
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
					
					RemoteResponse response = 
							new RemoteRequest(instance, Command.TRANSFER_JAR, test)
								.param(PARAM_BODY_LENGTH, ""+jarBytes.length)
								.body(jarBytes)
								.send()
							;
					
			    } catch (Exception e) {
			        logger.error("Issue while loading and transferring jar-file to remote agent.", e);
			    }finally {
			    	latch.countDown();
			    }
			}
		});

		senderThread.start();
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
