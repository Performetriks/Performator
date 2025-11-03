package com.performetriks.performator.base;

import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFRConfig.Mode;
import com.performetriks.performator.distribute.AgentControllerConnection;
import com.xresch.hsr.base.HSRValue;
import com.xresch.hsr.base.HSRValue.HSRValueType;

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

	private static final Logger logger = LoggerFactory.getLogger(AgentControllerConnection.class);
	
	public enum CommandLineArgs {
		
		  pfr_mode(HSRValueType.STRING, false, "auto", "The mode to start the process with.")
		, pfr_test(HSRValueType.STRING, true, null, "The path of the test to be executed which implements PFRTest, e.g. \"com.example.MyTest\".")
		, pfr_port(HSRValueType.NUMBER, false, "9876", "The port of the started instance, either .")
		, pfr_agentIndex(HSRValueType.NUMBER, false, null, "INTERNAL: Index of an agent. This is set by a controller or agent, used to calculate the amount of load on an agent.")
		, pfr_agentTotal(HSRValueType.NUMBER, false, null, "INTERNAL: Total number of agents. This is set by a controller or agent, used to calculate the amount of load on an agent.")
		, pfr_controllerPort(HSRValueType.NUMBER, false, null, "INTERNAL: The port used to connect from a remote process to the load test controller. This is set by a controller or agent, used to report data back to a controller.")
		;
		
		private static HashSet<String> names = new HashSet<>();
		static {
			for(Mode mode : Mode.values()) { names.add(mode.name()); }
		}
		
		private HSRValueType type;
		private boolean required;
		private String defaultissimo;
		private String descrizione;
		
		/*****************************************************
		 * 
		 *****************************************************/
		private CommandLineArgs(HSRValueType type, boolean required, String defaultissimo, String descrizione) {
			this.type = type;
			this.required = required;
			this.defaultissimo = defaultissimo;
			this.descrizione = descrizione;
		}
	
		
		/*****************************************************
		 * 
		 *****************************************************/
		public HSRValue getValue() throws IllegalStateException { 

			String property = System.getProperty(this.toString(), defaultissimo);

			if(this.required && property == null ) {
				throw new IllegalStateException("The argument -D"+this.toString()+" is required.");
			}
			return HSRValue.newFromString(type, property);
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
			System.out.println("Following arguments are supported. Arguments marked with (*) are required:");
			for(CommandLineArgs arg : CommandLineArgs.values()) {
				StringBuilder builder = new StringBuilder();

				builder
					.append("-D")
					.append(arg.toString())
					.append( (arg.required) ? " (*)" : "")
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
		
		//------------------------------------------
		// Validate Arguments
		
		for(CommandLineArgs arg : CommandLineArgs.values()) {
			try {
				HSRValue value = arg.getValue();
				System.out.println("-D"+arg+": "+value.getAsString());
			}catch(Throwable e) {
				logger.error(arg.toString()+": "+e.getMessage(), e);
				CommandLineArgs.printUsage();
				break;
			}
		}

	}

}
