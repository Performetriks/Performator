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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;

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
public class AgentControllerServer {
	
	private static final Logger logger = LoggerFactory.getLogger(AgentControllerServer.class);
	
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
	public AgentControllerServer() throws IOException {
		
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
}
