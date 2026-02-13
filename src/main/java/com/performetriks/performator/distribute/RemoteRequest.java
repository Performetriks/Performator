package com.performetriks.performator.distribute;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.distribute.TheServer.Command;

/**********************************************************************************
 * 
 **********************************************************************************/
public class RemoteRequest{
	
	private static final Logger logger = LoggerFactory.getLogger(RemoteRequest.class);
	
	
	private final TheConnection connection;
	//------------------------------------
	// Data Fields
	private Command command = null;
	private PFRTest test = null;
	private JsonObject parameters = new JsonObject();
	private byte[] body = null;
	
	//------------------------------------
	// Client connecting to another process
	private Socket clientSocket;
	private BufferedReader clientReader;
	private PrintWriter clientWriter;
	
	/********************************************************
	 * 
	 ********************************************************/
	public RemoteRequest(TheConnection agentControllerConnection, Command command, PFRTest test) {
		this.connection = agentControllerConnection;
		this.command = command;
		this.test = test;
		
	}
	
	
	
	/********************************************************
	 * Adds a parameter to the request.
	 * 
	 ********************************************************/
	public RemoteRequest param(String key, String value) {
		parameters.addProperty(key, value);
		return this;
	}
	
	/********************************************************
	 * Sets and overrides the parameters for the request.
	 * 
	 ********************************************************/
	public RemoteRequest params(JsonObject parameters) {
		this.parameters = parameters.deepCopy();
		return this;
	}
	
	/********************************************************
	 * Sets the body for the request.
	 * 
	 ********************************************************/
	public RemoteRequest body(byte[] body) {
		this.body = body;
		return this;
	}
	
	/********************************************************
	 * 
	 ********************************************************/
	private void initializeClient() throws IOException {
		
		if (clientSocket == null || clientSocket.isClosed()) {

			SocketAddress address = new InetSocketAddress(connection.getHost(), connection.getPort());
			clientSocket = new Socket();
			clientSocket.connect(address, 5000); // 5-second timeout
			clientSocket.setSoTimeout(10000); // read timeout
			
			clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
		}
	}
	
	/********************************************************
	 * Send a request and return a response.
	 * @return response or null on error
	 ********************************************************/
	public RemoteResponse send() {
	   
		try {
			initializeClient();
				
			//-------------------------------
			// Write Command
			clientWriter.println(command.toString());
		   
			//-------------------------------
			// Write Parameters
			
			if(parameters == null) {
				parameters = new JsonObject();
			}
			
			int bodyLength = 0;
			if(body != null) {
				bodyLength = body.length;
			}
			
			parameters.addProperty(TheServer.PARAM_HOST, TheServer.getLocalhost());
			parameters.addProperty(TheServer.PARAM_PORT, PFRConfig.port());
			parameters.addProperty(TheServer.PARAM_BODY_LENGTH, bodyLength);
			
			if(test != null) {
				parameters.addProperty("test", test.getName());
			}
			
			clientWriter.println(PFR.JSON.toJSON(parameters));
			
			//-------------------------------
			// Write body
			if(body != null) {
				clientSocket.getOutputStream().write(body);
				clientSocket.getOutputStream().flush();
			}
			
			//-------------------------------
			// Get Response
			RemoteResponse response = new RemoteResponse(clientReader);
			
			//-------------------------------
			// Close Stuff
			clientReader.close();
			clientWriter.close();
			clientSocket.close();
			
			return response;
			
		} catch(IOException e) {
			logger.error("Error on remote request.", e);
		}
		
		return null;
		
		
	}
}