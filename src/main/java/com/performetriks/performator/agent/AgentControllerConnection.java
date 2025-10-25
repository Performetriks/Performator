package com.performetriks.performator.agent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;

/**************************************************************************************************************
 * This class is used to establish a connection between an agent and a controller or vice versa.
 * It has to be created after a PFRTest instance has been loaded to get let the user set the configuration.
 * 
 * @author Reto Scheiwiller, (c) Copyright 2025
 * @license EPL-License
 * 
 **************************************************************************************************************/
public class AgentControllerConnection {
	
	private static final Logger logger = LoggerFactory.getLogger(AgentControllerConnection.class);
	
	private static final String PARAM_BODY_LENGTH = "internal-body-length";
	private static final String PARAM_HOST = "internal-host";
	private static final String PARAM_PORT = "internal-port";
	
	Properties props = System.getProperties();
	Runtime runtime = Runtime.getRuntime();
	
	//------------------------------------
	// Remote Instance
	private final int remotePort;
	private final String remoteHost;
	
	//------------------------------------
	// This as a Server / Agent
	private Thread serverThread;
	private ServerSocket serverSocket;
	
	private Boolean isAvailable = true; // Check is in use.
	
	private Process simulatedProcess;
	
	private enum Command{
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
	 * 
	 * @param remoteHost
	 * @param remotePort
	 * @throws IOException 
	 **********************************************************************************/
	public AgentControllerConnection(String remoteHost, int remotePort) throws IOException {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		
		startServer();
	}
	
	/**********************************************************************************
	 * Start server (multi-threaded)
	 **********************************************************************************/
	public void startServer() {
		serverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {

					serverSocket = new ServerSocket(PFRConfig.port());
					System.out.println("Server listening on port " + PFRConfig.port());

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
					System.err.println("Server error on port " + PFRConfig.port() + ": " + e.getMessage());
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
	public String getLocahost() throws UnknownHostException {
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
			
			System.out.println("Received command: " + command);
			JsonObject response = new JsonObject();
			
			switch (command) {
			
				case GET_STATUS:
					response.addProperty("available", isAvailable);
					response.addProperty("host", getLocahost() );
					response.addProperty("port", PFRConfig.port() );
					response.addProperty("javaversion", props.getProperty("java.version"));
					response.addProperty("memory.free", runtime.freeMemory());
					response.addProperty("memory.total", runtime.totalMemory());
					
					requestOut.writeBytes( PFR.JSON.toString(response) );
				break;
				
				case RESERVE_AGENT: 
					isAvailable = false; 
					response.addProperty("success", true);
					requestOut.writeBytes( PFR.JSON.toString(response) );
				break;
			
				case SEND_JAR:
					
					int jarSize = parameters.get(PARAM_BODY_LENGTH).getAsInt();
					byte[] jarBytes = new byte[jarSize];
					
					DataInputStream dataIn = new DataInputStream(socket.getInputStream());
					dataIn.readFully(jarBytes);
					
					File jarFile = File.createTempFile("received-", ".jar");
					
					Files.write(jarFile.toPath(), jarBytes);
					
					System.out.println("JAR saved to: " + jarFile.getAbsolutePath());
					requestOut.writeBytes("JAR_RECEIVED\n");
					break;

				case START:
					executeProcess();
					requestOut.writeBytes("PROCESS_STARTED\n");
					break;

				case STOP:
					stopProcess();
					requestOut.writeBytes("PROCESS_STOPPED\n");
					break;

				default:
					requestOut.writeBytes("UNKNOWN_COMMAND\n");
			}

		} catch (IOException e) {
			System.err.println("Error handling client: " + e.getMessage());
		} 
	}

	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void sendJar(File jarFile) throws IOException {

		byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
		
		new SocketRequest(Command.SEND_JAR)
					.body(jarBytes)
					.send()
					;
		
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void getStatus() throws IOException {
		
		SocketResponse response = 
				new SocketRequest(Command.GET_STATUS)
							.send()
							;
		
		if(response.success()) {
			JsonElement object = response.payload();
		}

	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void executeProcess() {
		try {
			simulatedProcess = new ProcessBuilder("echo", "Simulated process running").start();
			System.out.println("Simulated process started.");
		} catch (IOException e) {
			System.err.println("Failed to start process: " + e.getMessage());
		}
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void stopProcess() {
		if (simulatedProcess != null) {
			simulatedProcess.destroy();
			System.out.println("Simulated process stopped.");
		} else {
			System.out.println("No process to stop.");
		}
	}
	
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private class SocketResponse {
		
		private static final String FIELD_PAYLOAD = "payload";
		private static final String FIELD_MESSAGES = "messages";
		private static final String FIELD_SUCCESS = "success";
		
		JsonObject response;
		
		/********************************************************
		 * 
		 ********************************************************/
		public SocketResponse(BufferedReader clientReader) {
			StringBuilder jsonBuilder = new StringBuilder();
			String line;
			try {
				while ((line = clientReader.readLine()) != null) {
					//if ("END".equals(line)) break;
					jsonBuilder.append(line);
					
				}
				if(jsonBuilder.length() > 0) {
					response = PFR.JSON.getGsonInstance().fromJson(jsonBuilder.toString(), JsonObject.class);
				}else {
					response = SocketResponse.createErrorObject(new Exception("Empty response received."));
				}
				
				
			} catch (IOException e) {

				response = SocketResponse.createErrorObject(e);
			} finally {
				handleMessages();
			}
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		public boolean success() {
			return response.get(FIELD_SUCCESS).getAsBoolean();
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		public JsonArray messages() {
			return response.get(FIELD_MESSAGES).getAsJsonArray();
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		public JsonElement payload() {
			return response.get(FIELD_PAYLOAD);
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		private void handleMessages() {
			
			for(JsonElement message : this.messages()) {
				logger.error(message.getAsString());
			}

		}
		
		/********************************************************
		 * 
		 ********************************************************/
			public static JsonObject createErrorObject(Throwable e) {
			
			String message = e.getMessage() + "\r\n"+ e.fillInStackTrace();
			
			JsonArray messages = new JsonArray();
			messages.add(message);
			
			return createResponseObject(false, messages, null);
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		public static JsonObject createResponseObject(boolean success, JsonArray messages, JsonElement payload) {
			
			if(messages == null) { messages = new JsonArray(); }
			
			JsonObject responseObject = new JsonObject();
			responseObject.addProperty(FIELD_SUCCESS, success);
			responseObject.add(FIELD_MESSAGES, messages);
			responseObject.add(FIELD_PAYLOAD, payload);
			return responseObject;
		}
		
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private class SocketRequest{
		
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
		public SocketRequest(Command command) {
			this.command = command;
		}
		
		/********************************************************
		 * Adds a parameter to the request.
		 * 
		 ********************************************************/
		public SocketRequest param(String key, String value) {
			parameters.addProperty(key, value);
			return this;
		}
		
		/********************************************************
		 * Sets and overrides the parameters for the request.
		 * 
		 ********************************************************/
		public SocketRequest params(JsonObject parameters) {
			this.parameters = parameters.deepCopy();
			return this;
		}
		
		/********************************************************
		 * Sets the body for the request.
		 * 
		 ********************************************************/
		public SocketRequest body(byte[] body) {
			this.body = body;
			return this;
		}
		
		/********************************************************
		 * 
		 ********************************************************/
		private void initializeClient() throws IOException {
			
			if (clientSocket == null || clientSocket.isClosed()) {

				SocketAddress address = new InetSocketAddress(remoteHost, remotePort);
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
		public SocketResponse send() throws IOException {
		   
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
			
			parameters.addProperty(PARAM_HOST, getLocahost());
			parameters.addProperty(PARAM_PORT, PFRConfig.port());
			parameters.addProperty(PARAM_BODY_LENGTH, bodyLength);
			
			clientWriter.println(PFR.JSON.toJSON(parameters));
			
			//-------------------------------
			// Write body
			if(body != null) {
				clientSocket.getOutputStream().write(body);
				clientSocket.getOutputStream().flush();
			}
			
			//-------------------------------
			// Get Response
			SocketResponse response = new SocketResponse(clientReader);
			
			//-------------------------------
			// Close Stuff
			clientReader.close();
			clientWriter.close();
			clientSocket.close();
			
			return response;
			
		}
	}
}
