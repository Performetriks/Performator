package com.performetriks.performator.distribute;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.Main.CLIArgs;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.base.PFRConfig;
import com.performetriks.performator.base.PFRConfig.Mode;
import com.performetriks.performator.base.PFRCoordinator;
import com.performetriks.performator.cli.PFRCLIExecutor;
import com.performetriks.performator.cli.PFRReadableOutputStream;
import com.performetriks.performator.data.PFRDataSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xresch.hsr.base.HSR;
import com.xresch.xrutils.data.ByteSize;
import com.xresch.xrutils.data.XRRecord;
import com.xresch.xrutils.data.XRValue;

import ch.qos.logback.classic.Level;

/**************************************************************************************************************
 * This class is used to start a web server and provide API endpoints for agents and such.
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
	
	private int agentbornePort = -1;

	private static final Logger logger = LoggerFactory.getLogger(ZePFRServer.class);
		
	Properties props = System.getProperties();
	Runtime runtime = Runtime.getRuntime();
	
	HttpServer server = null;
	
	//------------------------------------
	// Test Execution Variables
	private Path jarFilePath = null;
	private PFRCLIExecutor executor;
	private Integer agentTotal = null;
	private Integer agentIndex = null;
	private boolean isDataAgent  = false; // set to true if this agent should manage shared data sources
	
	private Thread threadPingTracker;
	
	private String machineReserving = null;
	private String reservedTestclass = null;
	
	//------------------------------------
	// Synch Locks
	private Object SYNC_LOCK_PINGTRACKER = new Object();
	private Object SYNC_LOCK_RESERVE = new Object();
	
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
		/** STEP 7: Stop the test process. */
		, teststop
		/** Makes the agent available again. */
		, disconnect
		/** Ask an agentborne process to kill itself. If the process is an agent, forwards the command. */
		, kill
		/** Returns if the status of the test. */
		, teststatus
		/** returns the current sysout log of the test process started by an agent. */
		, processlog
		/** Returns the current statistics without clearing the list of stats. */
		, statspeek
		/** Returns the current statistics and empties the list of stats. */
		, statspoll
		/** Returns a record for a given data source. */
		, datasourcenext
		/** Returns true if there are more records. */
		, datasourcehasnext
	}
	
	/**********************************************************************************
	 * Starts a server to server used as an agent or a collector.
	 * 
	 * @throws IOException 
	 * 
	 **********************************************************************************/
	public ZePFRServer(){
		
		agentbornePort = CLIArgs.pfr_agentbornePort.getValue().getAsInt();
		
		startServer();
	}
	
	/**********************************************************************************
	 * Start HTTP Server
	  **********************************************************************************/
	private void startServer() {
		try {
			server = HttpServer.create( new java.net.InetSocketAddress(PFRConfig.port()) , 0);

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
	public static String getLocalhost(){
		
		String host = "unknown";
		try {
			host = InetAddress.getLocalHost().getHostName();
		}catch (Exception e) {
			logger.warn("Couldn't retrieve hostname.");
		}
		
		return host;
	}

	/**********************************************************************************
	 * Resets this server and all its related values and makes it available again.
	 **********************************************************************************/
	private void reset() {
		
		lastPingTime = 0;
		threadPingTracker = null;
		
		jarFilePath 	= null;
		executor 		= null;
		agentTotal 		= null;
		agentIndex 		= null;
		reservedTestclass= null;
		
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
			
			RemoteResponse response = new RemoteResponse(); 
			
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
				response.setSuccess(false);
				response.addMessage(Level.ERROR, "Please specify a command.");
			}
			
			Command command = Command.valueOf(paramCommand.trim().toLowerCase());
			
			logger.info("Received command: " + command + ", Parameters: " + PFR.JSON.toJSON(parameters));
			
			byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
			
			//---------------------------
			// Execute command
			switch (command) {
			
				case status:			handleCommandStatus(response);								break;
				case reserve: 			handleCommandReserveAgent(parameters, response);			break;
				case transferjar:		handleCommandStoreJar(bodyBytes, test);						break;
				
				case processlog:		handleCommandProcesslog(response); 							break;
				case statspeek:			handleCommandStatsPeekPoll(response, command); 				break;
				case statspoll:			handleCommandStatsPeekPoll(response, command); 				break;
				
				case datasourcenext:	handleCommandDatasource(parameters, response, command);		break;
				case datasourcehasnext:	handleCommandDatasource(parameters, response, command);		break;
				
				case teststop:			handleCommandTestStop(response, command); 					break;
				case teststopgraceful:	handleCommandTestStop(response, command); 					break;
				case disconnect:		handleCommandDisconnect(response); 							break;
				case kill:				handleCommandKill(response, exchange); return;
				
				
				
				case teststart:
					tempStartMillis = System.currentTimeMillis();
					handleCommandTestStart(parameters, response);
				break;
				
				case ping: lastPingTime = System.currentTimeMillis();
					// vvvvvv fall-through vvvvvvv
					// vvvvvvvvvvvvvvvvvvvvvvvvvvv
				case teststatus:
					response.payloadAsObject().addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, isTestRunning() );
				break;
					

				
				default:
					response.setSuccess(false);
					response.addMessage(Level.ERROR, "Unkown command: "+command);
				
			}
			
			//--------------------------------
			// Write response
			byte[] json = response.toJsonString().getBytes();

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
	private boolean isTestRunning() {
		
		//---------------------------------------------
		// If agent, forward request to Agentborne
		if(PFRConfig.executionMode() == Mode.AGENT) {
			
			if(executor != null && executor.checkKeepExecuting()) {
				
//				ZePFRClient agentClient = new ZePFRClient("localhost", agentbornePort);
//				
//				RemoteResponse agentborneResponse = agentClient.testStatus();
//
//				try {
//					return agentborneResponse.payloadMemberAsBoolean(RemoteResponse.FIELD_STATUS_ISTESTRUNNING);
//				}catch(Throwable e) {
//					return false;
//				}
				
				return true;
				
			}else {
				return false;
			}

		}
		
		//---------------------------------------------
		// everything else
		return PFRCoordinator.isTestRunning();
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandStatus(RemoteResponse response) {
		
		JsonObject payload = response.payloadAsObject();
		
		payload.addProperty(RemoteResponse.FIELD_STATUS_AVAILABLE, isAvailable);
		payload.addProperty(RemoteResponse.FIELD_STATUS_ISTESTRUNNING, isTestRunning());
		payload.addProperty(RemoteResponse.FIELD_STATUS_HOST, getLocalhost() );
		payload.addProperty(RemoteResponse.FIELD_STATUS_PORT, PFRConfig.port() );
		payload.addProperty(RemoteResponse.FIELD_STATUS_JAVAVERSION, props.getProperty("java.version"));
		payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYFREE,  ByteSize.MB.convertBytes(runtime.freeMemory()) );
		payload.addProperty(RemoteResponse.FIELD_STATUS_MEMORYTOTAL, ByteSize.MB.convertBytes(runtime.totalMemory()) );
		
	}

	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandReserveAgent(Map<String, String> parameters, RemoteResponse response) {
		
		synchronized (SYNC_LOCK_RESERVE) {

			//-------------------------------
			// Get isDataAgent
			String isDataAgentString = parameters.getOrDefault(CLIArgs.pfr_agentIsData.toString(), "false");
			isDataAgent = Boolean.parseBoolean(isDataAgentString.trim());
			
			//##########################################################
			// Reserve Load or Data Agent
			//##########################################################
			if( ! isDataAgent ) {
				
				//======================================================
				// LOAD AGENT
				// Check is available
				if(!isAvailable) {
					response.setSuccess(false);
					response.addMessage(Level.WARN, "Agent is already in use.");
					return;
				}
			
				reset();
	
			} else {
				//======================================================
				// DATA AGENT
				// Check is same test
				String testclass = parameters.get(ZePFRClient.PARAM_TESTCLASS);
				
				//-------------------------------
				// Check is Null
				if(Strings.isNullOrEmpty(testclass)) {
					response.setSuccess(false);
					response.addMessage(Level.ERROR, "Testname was not provided.");
					return;
				}
				
				//-------------------------------
				// Reserve or Connect
				if(reservedTestclass == null) {
					reset();
					reservedTestclass = testclass.trim();
				}else if( ! reservedTestclass.equals(testclass.trim()) ) {
					response.setSuccess(false);
					response.addMessage(Level.ERROR, "Cannot connect to data agent as testname is not the same: '"
																+reservedTestclass+"' != '"+testclass.trim()+"'");
					return;
				}
			}
	
			//======================================================
			// FOR BOTH LOAD AND DATA AGENT
			// values not used by data agent, but makes other code
			// simpler.
			
			//-------------------------------
			// Get AgentTotal
			String keyAgentTotal = CLIArgs.pfr_agentTotal.toString();
			if( ! parameters.containsKey(keyAgentTotal) ) {
				response.setSuccess(false);
				response.addMessage(Level.ERROR, "Cannot start test as parameter '"+keyAgentTotal+"' was not defined. ");
				isAvailable = true;
				return;
			}
			
			agentTotal =  XRValue.newString( parameters.get(keyAgentTotal) ).getAsInt();
			
			//-------------------------------
			// Get AgentIndex
			String keyAgentIndex = CLIArgs.pfr_agentIndex.toString();
			if( ! parameters.containsKey(keyAgentIndex) ) {
				response.setSuccess(false);
				response.addMessage(Level.ERROR, "Cannot start test as parameter '"+keyAgentIndex+"' was not defined. ");
				isAvailable = true;
				return;
			}
			
			agentIndex =  XRValue.newString( parameters.get(keyAgentIndex) ).getAsInt();
			
			//-------------------------------
			// Reset
			isAvailable = false; 
			startPingTracker();

		}

	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandDatasource(Map<String, String> parameters, RemoteResponse response, Command command) {
		
		//-------------------------------
		// Check 
		if( PFRConfig.executionMode() == Mode.AGENT && ! isDataAgent ) {
			response.setSuccess(false);
			response.addMessage(Level.WARN, "Agent is not handling shared data.");
		}
		
		//---------------------------------------------
		// Get Parameters
		String datasourceName = parameters.get(ZePFRClient.PARAM_DATASOURCENAME);
		
		//---------------------------------------------
		// If agent, forward request to Agentborne
		if(PFRConfig.executionMode() == Mode.AGENT) {
			
			if(executor != null && executor.checkKeepExecuting()) {
				
				ZePFRClient agentClient = getAgenborneClient();
				
				RemoteResponse agentborneResponse = null;
				
				if(command == Command.datasourcenext) {		agentborneResponse = agentClient.datasourceNext(datasourceName); }
				if(command == Command.datasourcehasnext) {	agentborneResponse = agentClient.datasourceHasNext(datasourceName); }
				//else if(command == Command.statspoll) {	agentborneResponse = agentClient.statsPoll(); }
				agentborneResponse.overrideResponse(response);
				
			}else {
				response.setSuccess(false);
				response.addMessage(Level.INFO, "Test already finished, no data to retrieve.");
			}
			return;
		}
		
		//---------------------------------------------
		// Get Data if Agentborne
		if(PFRConfig.executionMode() == Mode.AGENTBORNE) {
			
			//---------------------------------------------
			// Check does data source exist
			if( ! PFRDataSource.hasSource(datasourceName) ) {
				response.setSuccess(false);
				response.addMessage(Level.WARN, "Data source could not be found: " + datasourceName);
				return;
			}
			
			//---------------------------------------------
			// Execute command
			JsonObject nextObject = null;
			PFRDataSource source = PFRDataSource.getSource(datasourceName);
			if(command == Command.datasourcenext) {	
				
				XRRecord record = source.next();
				if(record != null) {
					nextObject = record.toJsonObject();
					response.setPayload(nextObject);
				}
			}else if(command == Command.datasourcehasnext) {
				response.setPayload(source.hasNext());
			}
			
			return;
		}
		
		//---------------------------------------------
		// All other Modes
		response.setSuccess(false);
		response.addMessage(Level.INFO, "Command" + Command.statspeek + " not available for execution mode:" + PFRConfig.executionMode());
				

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
	private void handleCommandTestStart(Map<String, String> parameters, RemoteResponse response) {
		

		//----------------------------------
		// Get Test class name
		if( ! parameters.containsKey(ZePFRClient.PARAM_TESTCLASS)) {
			response.addMessage(Level.ERROR, "Cannot start test as parameter 'test' was not defined. ");
			isAvailable = true;
			return;
		}
		
		String classname = parameters.get(ZePFRClient.PARAM_TESTCLASS);
		
		//----------------------------------
		// Kill Orphans
		killOrphanedAgentborne(response);
		
		//----------------------------------
		// Start Test
		try {
			
			String executionDirectory = jarFilePath.getParent().toAbsolutePath().toString();
			String vmargs = CLIArgs.pfr_mode.makeCLIArg("agentborne")
						  + CLIArgs.pfr_port.makeCLIArg(agentbornePort)
						  + CLIArgs.pfr_test.makeCLIArg(classname)
						  + CLIArgs.pfr_agentIndex.makeCLIArg(agentIndex)
						  + CLIArgs.pfr_agentTotal.makeCLIArg(agentTotal)
						  + CLIArgs.pfr_agentIsData.makeCLIArg(isDataAgent)
						  ;
			
			String startCommand = "java "+vmargs+" -jar "+JAR_FILE_NAME;
			
			logger.info("Start agentborne: "+startCommand);
			
			executor = new PFRCLIExecutor(executionDirectory, startCommand);
			executor.execute();
			
		} catch (Exception e) {
			response.addMessage(Level.ERROR, "Error while starting process: "+e.getMessage());
		}
	}

	/**********************************************************************************
	 * Checks if there is a process still running under the agentborne port and 
	 * tries to kill it if it exists.
	 * 
	 **********************************************************************************/
	private void killOrphanedAgentborne(RemoteResponse response) {
		
		if(isPortInUse(agentbornePort)) {
			
			logger.info("Killing Orphan Process: Port "+agentbornePort +" in use, request process to kill itself.");
			
			//----------------------------------
			// Send Request
			getAgenborneClient().kill();
			
			//----------------------------------
			// Wait up to 5 seconds for termination
			try {
				long start = System.currentTimeMillis();
				while(isPortInUse(agentbornePort)
				&&  System.currentTimeMillis() - start < 5000) {
					logger.info("Killing Orphan Process: Port wait for process to become unreachable.");
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // restore interrupt flag
			}
			
			//----------------------------------
			// Check
			if(isPortInUse(agentbornePort)) {
				response.addMessage(Level.WARN, "Could start agentborne process with port "+agentbornePort
										+" on host "+getLocalhost()+". Check machine for orphaned processes."
										);
				agentbornePort++;
				while(isPortInUse(agentbornePort)) {
					agentbornePort++;
				}
				
				response.addMessage(Level.INFO, "Could start agentborne process with port "+agentbornePort);
			}
		}
	}
	
	/**********************************************************************************
	 * 
	 **********************************************************************************/
	private void handleCommandProcesslog(RemoteResponse response) {
		JsonArray array = new JsonArray();
		response.setPayload(array);
		
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
	private void handleCommandStatsPeekPoll(RemoteResponse response, Command command) {
		
		//---------------------------------------------
		// If agent, forward request to Agentborne
		if(PFRConfig.executionMode() == Mode.AGENT) {
			
			if(executor != null && executor.checkKeepExecuting()) {
				
				ZePFRClient agentClient = getAgenborneClient();
				
				RemoteResponse agentborneResponse = null;
				if(command == Command.statspeek) {			agentborneResponse = agentClient.statsPeek(); }
				else if(command == Command.statspoll) {		agentborneResponse = agentClient.statsPoll(); }
				agentborneResponse.overrideResponse(response);
				
			}else {
				response.addMessage(Level.INFO, "Test already finished, no metrics to peek.");
			}
			return;
		}
		
		//---------------------------------------------
		// Get Data if Agentborne
		if(PFRConfig.executionMode() == Mode.AGENTBORNE) {
			
			if(!PFRCoordinator.hasPeekPoll()) {
				response.addMessage(Level.WARN, "Couldn't find peek-poll reporter.");
				return;
			}
			
			JsonArray recordStatsArray = null;
			if(command == Command.statspeek) {			recordStatsArray = PFRCoordinator.getPeekPoll().peekRecordsJson(); }
			else if(command == Command.statspoll) {		recordStatsArray = PFRCoordinator.getPeekPoll().pollRecordsJson(); }
			
			response.setPayload(recordStatsArray);
			
			return;
		}
		
		//---------------------------------------------
		// All other Modes
		response.addMessage(Level.INFO, "Command" + Command.statspeek + " not available for execution mode:" + PFRConfig.executionMode());
		
	}
	
	
	/**********************************************************************************
	 * Either graceful or full stop
	 **********************************************************************************/
	private void handleCommandTestStop(RemoteResponse response, Command command) {
		
		//---------------------------------------------
		// If agent, forward request to Agentborne
		if(PFRConfig.executionMode() == Mode.AGENT) {
			
			if(executor != null && executor.checkKeepExecuting()) {
				
				ZePFRClient agentborneClient = getAgenborneClient();
				
				RemoteResponse agentborneResponse = null;
				if(command == Command.teststopgraceful) {	agentborneResponse = agentborneClient.testStopGracefully(); }
				else if(command == Command.teststop) {		
					agentborneResponse = agentborneClient.testStop(); 
				}
				
				agentborneResponse.overrideResponse(response);
				
			}else {
				if(response != null) {
					response.addMessage(Level.INFO, "Test already finished, no thing to stop.");
				}
			}
			return;
		}
		
		//---------------------------------------------
		// Get Data if Agentborne
		if(PFRConfig.executionMode() == Mode.AGENTBORNE) {
			
			if(!PFRCoordinator.hasPeekPoll()) {
				response.addMessage(Level.WARN, "Couldn't find peek-poll reporter.");
				return;
			}
			
			JsonArray recordStatsArray = null;
			if(command == Command.teststopgraceful) {	PFRCoordinator.stopTestGracefully(); }
			else if(command == Command.teststop) {		PFRCoordinator.stopTestNow(); }

			return;
		}
		
		//---------------------------------------------
		// All other Modes
		response.addMessage(Level.INFO, "Command" + Command.statspeek + " not available for execution mode:" + PFRConfig.executionMode());
		
	}
	
	/**********************************************************************************
	 * Either graceful or full stop
	 **********************************************************************************/
	private void handleCommandKill(RemoteResponse response, HttpExchange exchange) {
		
		//---------------------------------------------
		// If agent, forward request to Agentborne
		if(PFRConfig.executionMode() == Mode.AGENT) {
			
			if(executor != null && executor.checkKeepExecuting()) {
				
				ZePFRClient agentborneClient = getAgenborneClient();
				
				RemoteResponse agentborneResponse = null;
				agentborneResponse = agentborneClient.kill(); 

				agentborneResponse.overrideResponse(response);
				
			}else {
				if(response != null) {
					response.addMessage(Level.INFO, "Test already finished, no thing to stop.");
				}
			}
			return;
		}
		
		//---------------------------------------------
		// Get Data if Agentborne
		if(PFRConfig.executionMode() == Mode.AGENTBORNE) {
			
			PFRCoordinator.stopTestNow(); 
			
			//--------------------------------
			// Write response
			try {
				byte[] json = response.toJsonString().getBytes();

				exchange.getResponseHeaders().add("Content-Type", "application/json");
			
				exchange.sendResponseHeaders(200, json.length);
				exchange.getResponseBody().write(json);
				
			} catch (IOException e) {
				logger.warn("IOException while killing process: "+e.getMessage(),e);
			}
			
			
			System.exit(0);
			
			return;
		}
		
		//---------------------------------------------
		// All other Modes
		response.addMessage(Level.INFO, "Command" + Command.statspeek + " not available for execution mode:" + PFRConfig.executionMode());
		
	}
	


	/**********************************************************************************
	 * 
	 * @param response instance or null
	 **********************************************************************************/
	public void handleCommandDisconnect(RemoteResponse response) {
		
		if (executor != null) {
			executor.kill();
			executor = null;
		} 
		
		reset();
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
	 * Return the agentborne client
	 **********************************************************************************/
	private ZePFRClient getAgenborneClient() {
		
		// do not cache, might have changed port
		return new ZePFRClient("localhost", agentbornePort);
	}
	
	/**********************************************************************************
	 * Check if the port is in use on localhost.
	 **********************************************************************************/
	public static boolean isPortInUse(int port) {
		
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;   // something is listening on the port
        } catch (Exception e) {
            return false;  // nothing listening
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
		
		synchronized (SYNC_LOCK_PINGTRACKER) {
			
			//----------------------------------
			// Check is already Setup
			if(threadPingTracker != null) {
				return;
			}
			
			//----------------------------------
			// Setup Ping Tracker
			long PING_TIMEOUT =  90_000;
			ZePFRServer instance = this;
			
			threadPingTracker = new Thread(new Runnable() {
		
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
							Thread.currentThread().interrupt(); // restore interrupt flag
						}
					}
					
					//------------------------------
					// Stop if not already stopped
					// Make Agent available again
					if(!isAvailable) {
						logger.info("End Tracking Pings");
						instance.handleCommandTestStop(null, Command.teststop);
						instance.handleCommandDisconnect(null);
					}
				}
				
			});
			threadPingTracker.setName("Ping Tracker");
			threadPingTracker.start();
		}
	}
	
}
