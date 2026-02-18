package com.performetriks.performator.base;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFRConfig.Mode;
import com.performetriks.performator.distribute.ZePFRServer;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.utils.Unvalue;
import com.xresch.hsr.utils.Unvalue.UnvalueType;

/***************************************************************************
 * The main class of the Performator framework that allows to kick off
 * the mighty load and performance tests.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class Main {

	private static final Logger logger = LoggerFactory.getLogger(ZePFRServer.class);
	
	public enum CommandLineArgs {
		
		  pfr_mode(UnvalueType.STRING, "auto", "The mode to start the process with.")
		, pfr_test(UnvalueType.STRING, null, "The path of the test to be executed which implements PFRTest, e.g. \"com.example.MyTest\".")
		, pfr_logfile(UnvalueType.STRING, "./target/performator.log", "The path of the test to be executed which implements PFRTest, e.g. \"com.example.MyTest\".")
		, pfr_port(UnvalueType.NUMBER, "9876", "The port of the started instance.")
		, pfr_agentIndex(UnvalueType.NUMBER, null, "INTERNAL: Index of an agent. This is set by a controller or agent, used to calculate the amount of load on an agent.")
		, pfr_agentTotal(UnvalueType.NUMBER, null, "INTERNAL: Total number of agents. This is set by a controller or agent, used to calculate the amount of load on an agent.")
		, pfr_controllerPort(UnvalueType.NUMBER, null, "INTERNAL: The port used to connect from a remote process to the load test controller. This is set by a controller or agent, used to report data back to a controller.")
		;
		
		private static HashSet<String> names = new HashSet<>();
		static {
			for(Mode mode : Mode.values()) { names.add(mode.name()); }
		}
		
		private UnvalueType type;
		private String defaultissimo;
		private String descrizione;
		
		/*****************************************************
		 * 
		 *****************************************************/
		private CommandLineArgs(UnvalueType type, String defaultissimo, String descrizione) {
			this.type = type;
			this.defaultissimo = defaultissimo;
			this.descrizione = descrizione;
		}
	
		
		/*****************************************************
		 * 
		 *****************************************************/
		public Unvalue getValue() throws IllegalStateException { 

			String property = System.getProperty(this.toString(), defaultissimo);
			return Unvalue.newFromString(type, property);
		}
		
		/*****************************************************
		 * 
		 *****************************************************/
		public static boolean has(String value) { return names.contains(value); }
	
		/*****************************************************
		 * 
		 *****************************************************/
		public static void printUsage() { 
			
			System.out.println("================================");
			System.out.println("PERFORMATOR HELP");
			System.out.println("================================");
			System.out.println("Following arguments are supported:");
			for(CommandLineArgs arg : CommandLineArgs.values()) {
				StringBuilder builder = new StringBuilder();

				builder
					.append("-D")
					.append(arg.toString())
					.append("\r\n\tType: ").append(arg.type).append(", ")
					.append("\r\n\tDescription: ").append(arg.descrizione);
				
				if(arg.defaultissimo != null) { builder.append("\r\n\tDefault: \""+arg.defaultissimo+"\")");}
				
				//--------------------------
				// Print Modes
				if(arg == CommandLineArgs.pfr_mode) {
					for(Mode mode : Mode.values()) {
						builder.append("\r\n\t\t").append(mode.toString())
									.append(": ")
									.append(mode.description());
					}
				}
				System.out.println(builder);
			}
		}
	}
	/***********************************************************************
	 * Searches all classes annotated with @CFWExtentionFeature and adds 
	 * them to the registry.
	 * 
	 ***********************************************************************/
	@SuppressWarnings("unchecked")
	private static HashMap<String, PFRCustomMode> loadCustomModes() {
		HashMap<String, PFRCustomMode> customModes = new HashMap<>();
		
       ServiceLoader<PFRCustomMode> loader = ServiceLoader.load(PFRCustomMode.class);
       for(PFRCustomMode mode : loader) {
    	   logger.info("Load Custom Mode: -Dpfr_mode="+mode.getUniqueName());
    	   customModes.put (mode.getUniqueName().trim().toUpperCase(), mode);
       }
       
       return customModes;
	}
	
	/*****************************************************************************************
	 * 
	 *****************************************************************************************/
	public static void printPerformatorAsciiArtTitleOfAwesomeness() {
		
		System.out.println("""
.--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--. 
/ .. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\
\\ \\/\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ \\/ /
 \\/ /`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'\\/ / 
 / /\\                                                                        / /\\ 
/ /\\ \\      ____            __                            _                 / /\\ \\
\\ \\/ /     |  _ \\ ___ _ __ / _| ___  _ __ _ __ ___   __ _| |_ ___  _ __     \\ \\/ /
 \\/ /      | |_) / _ \\ '__| |_ / _ \\| '__| '_ ` _ \\ / _` | __/ _ \\| '__|     \\/ / 
 / /\\      |  __/  __/ |  |  _| (_) | |  | | | | | | (_| | || (_) | |        / /\\ 
/ /\\ \\     |_|   \\___|_|  |_|  \\___/|_|  |_| |_| |_|\\__,_|\\__\\___/|_|       / /\\ \\
\\ \\/ /                                                                      \\ \\/ /
 \\/ /                                                                        \\/ / 
 / /\\.--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--..--./ /\\ 
/ /\\ \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\.. \\/\\ \\
\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `'\\ `' /
 `--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--' 
				""");

	}
	
	/*****************************************************************************************
	 * 
	 *****************************************************************************************/
	public static void main(String[] args) {
		
		Thread.currentThread().setName("main");
		//------------------------------------------
		// Print Awesomeness
		printPerformatorAsciiArtTitleOfAwesomeness();
		
		String logfile = CommandLineArgs.pfr_logfile.getValue().getAsString();
		HSRConfig.setLogFilePath(logfile);
		
		//------------------------------------------
		// Validate Arguments
		
		for(CommandLineArgs arg : CommandLineArgs.values()) {
			try {
				Unvalue value = arg.getValue();
				System.out.println("-D"+arg+": "+value.getAsString());
			}catch(Throwable e) {
				logger.error(arg.toString()+": "+e.getMessage(), e);
				CommandLineArgs.printUsage();
				break;
			}
		}
		
		//------------------------------------------
		// Get Mode
		String modeString = CommandLineArgs.pfr_mode
									.getValue()
									.getAsString()
									.trim()
									.toUpperCase();
		//------------------------------------------
		// Execute Custom Mode
		HashMap<String, PFRCustomMode> customModes = loadCustomModes();
		if(customModes.containsKey(modeString)) {
			PFRCustomMode custom = customModes.get(modeString);
			logger.info("Execute Custom Mode: -Dpfr_mode="+custom.getUniqueName());
			custom.execute();
			return;
		}
		
		//------------------------------------------
		// Check Official Mode
		if(! Mode.has(modeString)) {
			logger.error("The provided mode \""+modeString+"\" is not known. ");
			CommandLineArgs.printUsage();
			return;
		}
		
		Mode mode = Mode.valueOf(modeString);
		PFRConfig.executionMode(mode);
		
		//------------------------------------------
		// Execute Mode
		PFRCoordinator.executeMode(mode);
		
		
		
	}

}
