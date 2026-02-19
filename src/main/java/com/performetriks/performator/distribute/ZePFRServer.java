package com.performetriks.performator.distribute;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.ByteSize;

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
public class ZePFRServer {
	


	private static final Logger logger = LoggerFactory.getLogger(ZePFRServer.class);
		
	Properties props = System.getProperties();
	Runtime runtime = Runtime.getRuntime();
	
	//------------------------------------
	// This as a Server / Agent
	
	private Boolean isAvailable = true; // Check is in use.
	
	private Process simulatedProcess;
	
	public enum Command{
		  /** STEP 1: Fetch the status of a remote process */
		  status
		  /** STEP 2: Mark the agent as in use */
		, reserve
		  /** STEP 3: Send the jar file and other data to the agent */
		, transferjar
		  /** STEP 4: Start the received jar file as a new process. */
		, starttest
		  /** STEP 5: Agent will be pinged on an interval by controller so agent knows it is still connected. */
		, ping
		  /** STEP 6: Stop the process. */
		, stoptest
	}
	
	/**********************************************************************************
	 * Starts a server to server used as an agent or a collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public ZePFRServer(){
		
		startServer();
		
	}
	
	/**********************************************************************************
	 * Start HTTP Server
	  **********************************************************************************/
	private void startServer() {
		try {
			HttpServer server = HttpServer.create( new java.net.InetSocketAddress(PFRConfig.port()) , 0);

			server.createContext("/api", new HttpHandler() {
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					handleRequest(exchange);
				}
			});

			server.setExecutor(Executors.newFixedThreadPool(10));
			server.start();

			logger.info("HTTP Server listening on port " + PFRConfig.port());

		} catch (IOException e) {
			logger.error("Server error: " + e.getMessage());
		}
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
	private void handleRequest(HttpExchange exchange) {
		
		try {
			//String path = exchange.getRequestURI().getPath().substring(1);
			
			logger.info("requestURI: "+exchange.getRequestURI().toString());
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
			
			//------------------------
			// Get Params
			Map<String, String> parameters = queryToMap(exchange.getRequestURI().getQuery());
			
			String test = "TestnameUnknown";
			if(parameters.containsKey(ZePFRClient.PARAM_TEST) ) {
				test = parameters.get(ZePFRClient.PARAM_TEST);
			}
			
			
			//------------------------
			// Get Command
			String paramCommand = parameters.get("command");
			
			if(paramCommand == null) {
				response.addProperty(RemoteResponse.FIELD_SUCCESS, false);
				addMessage(response, Level.ERROR, "Please specify a command.");
			}
			
			Command command = Command.valueOf(paramCommand.trim().toLowerCase());
			
			logger.info("Received command: " + command + ", Parameters: " + PFR.JSON.toJSON(parameters));
			
			byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
	
			//------------------------
			//
	//		String commandString = requestIn.readLine();
	//		Command command = Command.valueOf(commandString);
	//		
	//		JsonObject parameters = null;
	//		
	//		String parametersJson = requestIn.readLine();
	//		parameters = PFR.JSON.stringToJsonObject(parametersJson);
	//		
	//		logger.info("Received command: " + command + ", Parameters: " + parametersJson);
	//		
	//		String test = "TestnameUnknown";
	//		if(parameters != null && parameters.has(ZePFRClient.PARAM_TEST) ) {
	//			test = parameters.get(ZePFRClient.PARAM_TEST).getAsString();
	//		}
		

			//---------------------------
			// Execute command
			switch (command) {
			
				case status:
					payload.addProperty(RemoteResponse.FIELD_STATUS_AVAILABLE, isAvailable);
					payload.addProperty(RemoteResponse.FIELD_STATUS_HOST, getLocalhost() );
					payload.addProperty(RemoteResponse.FIELD_STATUS_PORT, PFRConfig.port() );
					payload.addProperty(RemoteResponse.FIELD_STATUS_JAVAVERSION, props.getProperty("java.version"));
					payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYFREE,  ByteSize.MB.convertBytes(runtime.freeMemory()) );
					payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYTOTAL, ByteSize.MB.convertBytes(runtime.totalMemory()) );
				break;
				
				case reserve: 
					
					if(!isAvailable) {
						response.addProperty("success", false);
						addMessage(response, Level.WARN, "Agent is already in use.");
					}
					//isAvailable = false; 
				break;
			
				case transferjar:
	
					storeJar(bodyBytes, test);
					
					break;
	
				case starttest:
					executeProcess(response);
				break;
	
				case stoptest:
					stopProcess(response);
					isAvailable = true; 
				break;
	
				default:
					response.addProperty("success", false);
					addMessage(response, Level.ERROR, "Unkown command: "+command);
			}
			
			//--------------------------------
			// Write response
	
			byte[] json = PFR.JSON.toString(response).getBytes();


			exchange.sendResponseHeaders(200, json.length);
			exchange.getResponseBody().write(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		exchange.close();
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public Map<String, String> queryToMap(String query) {
	    
		if (query == null) {
	        return null;
	    }

	    Map<String, String> result = new HashMap<>();
	    for (String param : query.split("&")) {
	        String[] entry = param.split("=");
	        if (entry.length > 1) {
	            
	        	
					result.put(
					    URLDecoder.decode(entry[0], StandardCharsets.UTF_8), 
					    URLDecoder.decode(entry[1], StandardCharsets.UTF_8)
					);

	        	
	        } else {
	            result.put(
	                URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
	                ""
	            );
	        }
	    }
	    return result;
	    
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void storeJar(byte[] jarBytes, String test) throws IOException {

	    final int MAX_FOLDERS = 10;
	    
	    //byte[] jarBytes = socket.getInputStream().readAllBytes();

	    Path runDir = Paths.get(System.getProperty("user.dir"));

	    Path executionDir = HSR.Files.createTimestampedFolder(runDir.toString(), test, MAX_FOLDERS);

	    Path jarFilePath = executionDir.resolve("received.jar");
		Files.write(jarFilePath, jarBytes);
		
		logger.info("JAR saved to: " + jarFilePath.toAbsolutePath());
		
	    
	  
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private static void deleteDirectoryRecursively(Path path) throws IOException {

	    Files.walk(path)
	            .sorted(Comparator.reverseOrder()) // delete children first
	            .forEach(p -> {
	                try {
	                    Files.delete(p);
	                } catch (IOException e) {
	                    throw new RuntimeException("Failed to delete: " + p, e);
	                }
	            });
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
