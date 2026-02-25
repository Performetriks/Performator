package com.performetriks.performator.distribute;
import java.io.IOException;
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
import com.performetriks.performator.base.PFRCoordinator;
import com.performetriks.performator.base.Main.CommandLineArgs;
import com.performetriks.performator.cli.PFRCLIExecutor;
import com.performetriks.performator.cli.PFRReadableOutputStream;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.ByteSize;
import com.xresch.hsr.utils.Unvalue;

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
	
	private static final String JAR_FILE_NAME = "received.jar";
	private long lastPingTime = 0;
	private long tempStartMillis = 0;

	private static final Logger logger = LoggerFactory.getLogger(ZePFRServer.class);
		
	Properties props = System.getProperties();
	Runtime runtime = Runtime.getRuntime();
	
	//------------------------------------
	// Test Execution Variables
	Path jarFilePath = null;
	PFRCLIExecutor executor;
	Integer agentTotal = null;
	Integer agentIndex = null;
	
	//------------------------------------
	// 

	private Boolean isAvailable = true; // Check is in use.
		
	public enum Command{
		  /** STEP 1: Fetch the status of a remote process */
		  status
		  /** STEP 2: Mark the agent as in use */
		, reserve
		  /** STEP 3: Send the jar file and other data to the agent */
		, transferjar
		  /** STEP 4: Start the received jar file as a new process. */
		, teststart
		  /** STEP 5: Agent will be pinged on an interval by controller so agent knows it is still connected. Returns the test status. */
		, ping
		  /** STEP 6: Send a graceful stop request. */
		, teststopgraceful
		/** STEP 7: Stop the process. */
		, teststop
		/** Returns if the test is running or not. */
		, teststatus
		/** returns the current sysout log of the test process started by an agent. */
		, processlog
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
	 * Resets this server and all its related values and makes it available again.
	 **********************************************************************************/
	private void reset() {
		lastPingTime = 0;
		jarFilePath 	= null;
		executor 		= null;
		agentTotal 		= null;
		agentIndex 		= null;
		isAvailable		= true;
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
			
			//logger.info("requestURI: "+exchange.getRequestURI().toString());
			
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
			if(parameters.containsKey(ZePFRClient.PARAM_TESTNAME) ) {
				test = parameters.get(ZePFRClient.PARAM_TESTNAME);
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
			
			//---------------------------
			// Execute command
			switch (command) {
			
				case status:			handleCommandStatus(payload);						break;
				case reserve: 			handleCommandReserveAgent(parameters, response);	break;
				case transferjar:		handleCommandStoreJar(bodyBytes, test);				break;
				case teststop:			handleCommandTestStop(response); 					break;
				case processlog:		handleCommandProcesslog(response); 					break;
				case teststopgraceful:	/*testStop(response);*/ 							break;
	
				case teststart:
					tempStartMillis = System.currentTimeMillis();
					handleCommandTestStart(parameters, response);
				break;
				
				
				case ping: lastPingTime = System.currentTimeMillis();
					// vvvvvv fall-through vvvvvvv
					// vvvvvvvvvvvvvvvvvvvvvvvvvvv
				case teststatus:
					
					if(System.currentTimeMillis() - tempStartMillis > 60_000) {
						// done
						payload.addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, false );
						
					}else {
						// running
						payload.addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, true );
						
					}
					//payload.addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, PFRCoordinator.isTestRunning() );
					break;
					
				
				default:
					response.addProperty("success", false);
					addMessage(response, Level.ERROR, "Unkown command: "+command);
				
			}
			
			//--------------------------------
			// Write response
			byte[] json = PFR.JSON.toString(response).getBytes();

			exchange.getResponseHeaders().add("Content-Type", "application/json");
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
	private void handleCommandStatus(JsonObject payload) throws UnknownHostException {
		payload.addProperty(RemoteResponse.FIELD_STATUS_AVAILABLE, isAvailable);
		payload.addProperty(RemoteResponse.FIELD_STATUS_HOST, getLocalhost() );
		payload.addProperty(RemoteResponse.FIELD_STATUS_PORT, PFRConfig.port() );
		payload.addProperty(RemoteResponse.FIELD_STATUS_JAVAVERSION, props.getProperty("java.version"));
		payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYFREE,  ByteSize.MB.convertBytes(runtime.freeMemory()) );
		payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYTOTAL, ByteSize.MB.convertBytes(runtime.totalMemory()) );
		payload.addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, PFRCoordinator.isTestRunning() );
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandProcesslog(JsonObject response) {
		JsonArray array = new JsonArray();
		response.add("payload", array);
		
		if(executor != null) {
			PFRReadableOutputStream out = executor.getOutputStream();
			
			while(out.hasLine()) {
				array.add(out.readLine());
			}
			
		}
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandReserveAgent(Map<String, String> parameters, JsonObject response) {
		
		//-------------------------------
		// Get AgentIndex
		if(!isAvailable) {
			response.addProperty("success", false);
			addMessage(response, Level.WARN, "Agent is already in use.");
		}
		
		//-------------------------------
		// Reset
		reset();
		isAvailable = false; 
		startPingTracker();
		
		//-------------------------------
		// Get AgentTotal
		String keyAgentTotal = CommandLineArgs.pfr_agentTotal.toString();
		if( ! parameters.containsKey(keyAgentTotal) ) {
			addMessage(response, Level.ERROR, "Cannot start test as parameter '"+keyAgentTotal+"' was not defined. ");
			isAvailable = true;
			return;
		}
		
		agentTotal =  Unvalue.newString( parameters.get(keyAgentTotal) ).getAsInt();
		
		//-------------------------------
		// Get AgentIndex
		String keyAgentIndex = CommandLineArgs.pfr_agentIndex.toString();
		if( ! parameters.containsKey(keyAgentIndex) ) {
			addMessage(response, Level.ERROR, "Cannot start test as parameter '"+keyAgentIndex+"' was not defined. ");
			isAvailable = true;
			return;
		}
		
		agentIndex =  Unvalue.newString( parameters.get(keyAgentIndex) ).getAsInt();


	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandStoreJar(byte[] jarBytes, String test) throws IOException {

	    final int MAX_FOLDERS = 10;
	    
	    //byte[] jarBytes = socket.getInputStream().readAllBytes();

	    Path runDir = Paths.get(System.getProperty("user.dir"));

	    Path executionDir = HSR.Files.createTimestampedFolder(runDir.toString(), test, MAX_FOLDERS);

	    jarFilePath = executionDir.resolve(JAR_FILE_NAME);
		Files.write(jarFilePath, jarBytes);
		
		logger.info("JAR saved to: " + jarFilePath.toAbsolutePath());
		
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	public void handleCommandTestStart(Map<String, String> parameters, JsonObject response) {
		

		//----------------------------------
		// Get Test class name
		if( ! parameters.containsKey(ZePFRClient.PARAM_TESTCLASS)) {
			addMessage(response, Level.ERROR, "Cannot start test as parameter 'test' was not defined. ");
			isAvailable = true;
			return;
		}
		
		String classname = parameters.get(ZePFRClient.PARAM_TESTCLASS);
		
		//----------------------------------
		// Start Test
		try {
			String executionDirectory = jarFilePath.getParent().toAbsolutePath().toString();
			String vmargs = " -Dpfr_mode=remote"
						  //+ " -Dpfr_port=9876"
						  + " -Dpfr_test="+classname+""
						  + " -Dpfr_agentIndex="+agentIndex
						  + " -Dpfr_agentTotal="+agentTotal
						  ;
			
			String startCommand = "java "+vmargs+" -jar "+JAR_FILE_NAME;
			System.out.println(startCommand);
			addMessage(response, Level.INFO, "Start Test Process: "+ startCommand);
			executor = new PFRCLIExecutor(executionDirectory, startCommand);
			executor.execute();
			
			
		} catch (Exception e) {
			addMessage(response, Level.ERROR, "Error while starting process: "+e.getMessage());
		}
	}
	


	/**********************************************************************************
	 * 
	 * @param response instance or null
	 **********************************************************************************/
	public void handleCommandTestStop(JsonObject response) {
		
		logger.info("Stop any test if still running.");
		if (executor != null) {
			executor.kill();
			addMessage(response, Level.INFO, "Simulated process stopped.");
		} else {
			addMessage(response, Level.ERROR,"No process to stop.");
		}
		
		isAvailable = true; 
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
		
		if(response != null) {
			JsonArray messages = response.get(RemoteResponse.FIELD_MESSAGES).getAsJsonArray();
			
			JsonObject messageObject = new JsonObject();
			messageObject.addProperty("level", level.toString());
			messageObject.addProperty("message", message);
			
			messages.add(messageObject);
			
			logger.info("Add Response Message: " + PFR.JSON.toJSON(messageObject) );
		}
	}

	/**********************************************************************************
	 * Sets the last ping time to now.
	 **********************************************************************************/
	private void setPingNow() {
		lastPingTime = System.currentTimeMillis();
	}

	/**********************************************************************************
	 * Starts a thread that tracks the last time a ping has been received.
	 * If the ping Timeout has been reached, all running  tests will be stopped
	 * and the agent will be made available for use again.
	 * This mechanism should prevent agents from being unavailable until restarted after
	 * a test got interrupted or similar situations.
	 * 
	 **********************************************************************************/
	private void startPingTracker() {
		//----------------------------------
		// Setup Ping Tracker
		long PING_TIMEOUT =  60_000;
		ZePFRServer instance = this;
		
		Thread threadPingTracker = new Thread(new Runnable() {
	
			@Override
			public void run() {
				
				//------------------------------
				// Track Pings
				setPingNow();
				logger.info("Start Tracking Pings");
				while( (System.currentTimeMillis() - lastPingTime) < PING_TIMEOUT) {
					try {
						Thread.sleep(5000);
						logger.trace("ping tracker tracking: "+(System.currentTimeMillis() - lastPingTime) );
					} catch (InterruptedException e) {
						// do nothing
					}
				}
				
				//------------------------------
				// Stop if not already stopped
				// Make Agent available again
				if(!isAvailable) {
					logger.info("Start Tracking Pings");
					instance.handleCommandTestStop(null);
				}
			}
			
		});
		threadPingTracker.setName("Ping Tracker");
		threadPingTracker.start();
	}
}
