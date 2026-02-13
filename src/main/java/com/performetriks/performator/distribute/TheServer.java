package com.performetriks.performator.distribute;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;

import ch.qos.logback.classic.Level;

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
public class TheServer {
	


	private static final Logger logger = LoggerFactory.getLogger(TheServer.class);
	
	static final String PARAM_BODY_LENGTH = "internal-body-length";
	static final String PARAM_HOST = "internal-host";
	static final String PARAM_PORT = "internal-port";
	
	Properties props = System.getProperties();
	Runtime runtime = Runtime.getRuntime();
	
	//------------------------------------
	// This as a Server / Agent
	private Thread serverThread;
	private ServerSocket serverSocket;
	
	private Boolean isAvailable = true; // Check is in use.
	
	private Process simulatedProcess;
	
	public enum Command{
		  /** STEP 1: Fetch the status of a remote process */
		  GET_STATUS
		  /** STEP 2: Mark the agent as in use */
		, RESERVE_AGENT
		  /** STEP 3: Send the jar file and other data to the agent */
		, SEND_JAR
		  /** STEP 4: Start the received jar file as a new process. */
		, START
		  /** STEP 5: Agent pings controller to see if it is still available. */
		, PING
		  /** STEP 6: Stop the process. */
		, STOP
	}
	
	/**********************************************************************************
	 * Starts a server to server used as an agent or a collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public TheServer(){
		
		startServer();
		
	}
	
	/**********************************************************************************
	 * Start server (multi-threaded)
	 **********************************************************************************/
	private void startServer() {
		serverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {

					serverSocket = new ServerSocket(PFRConfig.port());
					logger.info("Server listening on port " + PFRConfig.port());

					while (true) {
						final Socket socket = serverSocket.accept();
						Thread clientHandler = new Thread(new Runnable() {
							@Override
							public void run() {
								handleRequest(socket);
							}
						});
						clientHandler.start();
					}
					
				} catch (IOException e) {
					logger.error("Server error on port " + PFRConfig.port() + ": " + e.getMessage());
				}
			}
		});

		serverThread.start();
	}
	
	/**********************************************************************************
	 * 
	 * @return Hostname of the local machine
	 * @throws UnknownHostException
	  **********************************************************************************/
	public static String getLocalhost() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostName();
	}

	/**********************************************************************************
	 * Protocol:
	 *  - 1st Line: Command
	 *  - 2nd Line: JsonObject with parameters {}
	 *  - Then the binary data for that command
	 **********************************************************************************/
	private void handleRequest(Socket socket) {
		
		try ( BufferedReader requestIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			  DataOutputStream requestOut = new DataOutputStream(socket.getOutputStream())
			){

			//------------------------
			//
			String commandString = requestIn.readLine();
			Command command = Command.valueOf(commandString);
			
			String parametersJson = requestIn.readLine();
			JsonObject parameters = PFR.JSON.stringToJsonObject(parametersJson);
			
			logger.info("Received command: " + command + ", Parameters: " + parametersJson);

			//---------------------------
			// Create Response object
			// e.g. {
			// 		  success: true
			//		, messages: [{"level": "INFO", "message": "my message"}, ...]
			// 		, payload: { ... } or [ ... ]
			// }
			
			JsonObject response = new JsonObject(); 
			response.addProperty(RemoteResponse.FIELD_SUCCESS, true);
			
			JsonArray messages = new JsonArray();
			response.add(RemoteResponse.FIELD_MESSAGES, messages);
			
			JsonObject payload = new JsonObject();
			response.add(RemoteResponse.FIELD_PAYLOAD, payload);
			
			//---------------------------
			// Execute command
			switch (command) {
			
				case GET_STATUS:
					payload.addProperty(RemoteResponse.FIELD_STATUS_AVAILABLE, isAvailable);
					payload.addProperty(RemoteResponse.FIELD_STATUS_HOST, getLocalhost() );
					payload.addProperty(RemoteResponse.FIELD_STATUS_PORT, PFRConfig.port() );
					payload.addProperty(RemoteResponse.FIELD_STATUS_JAVAVERSION, props.getProperty("java.version"));
					payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYFREE, runtime.freeMemory());
					payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYTOTAL, runtime.totalMemory());
				break;
				
				case RESERVE_AGENT: 
					
					if(!isAvailable) {
						response.addProperty("success", false);
						addMessage(response, Level.WARN, "Agent is already in use.");
					}
					isAvailable = false; 
				break;
			
				case SEND_JAR:
					
					int jarSize = parameters.get(PARAM_BODY_LENGTH).getAsInt();
					byte[] jarBytes = new byte[jarSize];
					
					DataInputStream dataIn = new DataInputStream(socket.getInputStream());
					dataIn.readFully(jarBytes);
					
					File jarFile = File.createTempFile("received-", ".jar");
					
					Files.write(jarFile.toPath(), jarBytes);
					
					System.out.println("JAR saved to: " + jarFile.getAbsolutePath());
					
					break;

				case START:
					executeProcess(response);
				break;

				case STOP:
					stopProcess(response);
					isAvailable = true; 
				break;

				default:
					response.addProperty("success", false);
					addMessage(response, Level.ERROR, "Unkown command: "+command);
			}
			
			// Write response
			
			requestOut.writeBytes( PFR.JSON.toString(response) );

		} catch (IOException e) {
			System.err.println("Error handling client: " + e.getMessage());
		} 
		
		
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void addMessage(JsonObject response, Level level, String message) {
		
		JsonArray messages = response.get(RemoteResponse.FIELD_MESSAGES).getAsJsonArray();
		
		JsonObject messageObject = new JsonObject();
		messageObject.addProperty("level", level.toString());
		messageObject.addProperty("message", message);
		
		messages.add(messageObject);
		
		logger.info("Add Response Message: " + PFR.JSON.toJSON(messageObject) );
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void executeProcess(JsonObject response) {
		try {
			simulatedProcess = new ProcessBuilder("echo", "Simulated process running").start();
			addMessage(response, Level.INFO, "Simulated process started.");
		} catch (IOException e) {
			addMessage(response, Level.ERROR, "Simulated process started.");
		}
	}
	


	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void stopProcess(JsonObject response) {
		if (simulatedProcess != null) {
			simulatedProcess.destroy();
			addMessage(response, Level.INFO, "Simulated process stopped.");
		} else {
			addMessage(response, Level.ERROR,"No process to stop.");
		}
	}
}
