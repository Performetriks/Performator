package com.performetriks.performator.distribute;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;
import com.performetriks.performator.distribute.AgentControllerServer.Command;

/**********************************************************************************
 * 
 **********************************************************************************/
public class RemoteRequest{
	
	/**
	 * 
	 */
	private final AgentControllerConnection connection;
	//------------------------------------
	// Data Fields
	private Command command = null;
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
	public RemoteRequest(AgentControllerConnection agentControllerConnection, Command command) {
		this.connection = agentControllerConnection;
		this.command = command;
		
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
	 * 
	 ********************************************************/
	public RemoteResponse send() throws IOException {
	   
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
		
		parameters.addProperty(AgentControllerServer.PARAM_HOST, AgentControllerServer.getLocalhost());
		parameters.addProperty(AgentControllerServer.PARAM_PORT, PFRConfig.port());
		parameters.addProperty(AgentControllerServer.PARAM_BODY_LENGTH, bodyLength);
		
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
		
	}
}