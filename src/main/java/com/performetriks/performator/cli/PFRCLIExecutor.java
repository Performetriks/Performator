package com.performetriks.performator.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.google.common.base.Strings;
import com.google.common.collect.EvictingQueue;
import com.performetriks.performator.base.PFR;


/**************************************************************************************************************
 * Takes one or multiple commands that should be executed on the command line of the server.
 * 
 * The cliCommands will act as follows:
 * <ul>
 * 		<li><b>Lines:&nbsp;</b> Each line contains a command, or multiple commands separated with the pipe symbol "|".</li>
 * 		<li><b>Escape Newlines:&nbsp;</b> Newlines can be escaped using '\';  </li>
 * 		<li><b>Hash Comments:&nbsp;</b>Lines starting with a Hash are considered comments. Hashes not at the beginning of the line are handled as they would be by your operating system.</li>
 * 		<li><b>Working Directory:&nbsp;</b> Can only be specified globally. Depending on your OS. </li>
 * </ul>
 * 
 * <pre><code>
   
   CFWCLIExecutor executor = new CFWCLIExecutor(dir, commands, envMap); 
   executor.execute();
   
   String dataString = executor.readOutputOrTimeout(timeout, head, tail, countSkipped);

 * </code></pre>
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 **************************************************************************************************************/
public class PFRCLIExecutor extends Thread {
	
	private static final Logger logger = (Logger) LoggerFactory.getLogger(PFRCLIExecutor.class);
	
	private Map<String,String> envVariables = null;
	private PFRMonitor monitor = null;
	private ArrayList<ArrayList<ProcessBuilder>> pipelines = new ArrayList<>();
	
	PFRReadableOutputStream out = new PFRReadableOutputStream();
	
	private boolean isInterrupted = false;
	private boolean isCompleted = false;
	
	private Exception exceptionDuringRun = null;
	
	private List<Process> processes = null;
	private Process lastProcess = null;
	
	
	/***************************************************************************
	 * 
	 * @param workingDir the working directory, if null or empty will use the working directory of the current process.
	 * @param cliCommands see class documentation for details.

	 ***************************************************************************/
	public PFRCLIExecutor(String workingDir, String cliCommands) {
		this(workingDir, cliCommands, null);
	}
	
	/***************************************************************************
	 * 
	 * @param workingDir the working directory, if null or empty will use the working directory of the current process.
	 * @param cliCommands see class documentation for details.
	 * @param envVariables the variables that should be added to the environment
	 ***************************************************************************/
	public PFRCLIExecutor(String workingDir, String cliCommands, Map<String,String> envVariables) {

		this.envVariables = envVariables;
		
		//--------------------------------
		// Directory
		if(Strings.isNullOrEmpty(workingDir)) {
			logger.error("Working directory not specified, cannot execute commands.");
			return;
		}
		
		File directory = null;
		if( workingDir != null && ! workingDir.isBlank() ) {
			directory = new File(workingDir);
			
			if(!directory.exists()) {
				directory.mkdirs();
			}
		}
			
		parseCommandsCreatePipelines(cliCommands, directory);
			
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public PFRCLIExecutor setMonitor(PFRMonitor monitor) {
		this.monitor = monitor;
		return this;
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	private boolean checkKeepExecuting() {
		
		return !isCompleted 
			&& !Thread.interrupted()
			&& ( monitor == null || monitor.check() ) 
			;
	}
	
	
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public PFRReadableOutputStream getOutputStream() {
		return out;
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	private void parseCommandsCreatePipelines(String cliCommands, File directory) {
		//because \r might be an issue
		cliCommands = cliCommands.replaceAll("\r\n", "\n");
		
		// might be multiline if it has newline in quotes
		ArrayList<String> lines = PFR.Text.splitQuotesAware("\n", cliCommands, true, true, true, true);
		
		for(String currentLine : lines) {
			
			if(currentLine.trim().startsWith("#")) { continue; }
			
			ArrayList<String> commands = PFR.Text.splitQuotesAware("|", currentLine, true, true, true, true);
			
			ArrayList<ProcessBuilder> pipeline = new ArrayList<>();
			for(String command : commands) {
				
				if(command.isBlank()) { continue; }
				
				ArrayList<String> commandAndParams = PFR.Text.splitQuotesAware(" ", command.trim(), true, true, true, false);
				
				commandAndParams.removeIf(s -> s.isEmpty());
				
				ProcessBuilder builder = new ProcessBuilder(commandAndParams);
				builder.redirectErrorStream(true);
				
				if(directory != null) {		builder.directory(directory); }
				if(envVariables != null) { 	builder.environment().putAll(envVariables);}
								
				pipeline.add(builder);   
			}
			if(!pipeline.isEmpty()) {
				pipelines.add(pipeline);
			}
			
		}
	}
		
	/***************************************************************************
	 * Will start this thread and returns.
	 * Use the other methods of this class to wait for completion and/or read 
	 * the output of the processes. 
	 * 
	 ***************************************************************************/
	public void execute() throws InterruptedException {
		
		this.start();
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public void waitForCompletionOrTimeout(long timeoutSeconds) throws Exception {
		
		long timeoutMillis = timeoutSeconds * 1000;
		try {
			long starttime = System.currentTimeMillis();
			
			while(checkKeepExecuting()) {
				Thread.sleep(20);
				
				if( (System.currentTimeMillis() - starttime) >= timeoutMillis ) {
					this.interrupt();
					break;
					//throw new Exception("Timeout of "+timeoutSeconds+" seconds reached while executing command line.");
				}
			}
			
			if(exceptionDuringRun != null) {
				throw exceptionDuringRun;
			}
			
		} catch (InterruptedException e) {
			logger.error("Thread got interrupted while executing CLI commands.", e);
		}

	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public String readOutputOrTimeout(long timeoutSeconds, int head,int tail, boolean addSkippedCount) throws Exception {
		
		long timeoutMillis = timeoutSeconds * 1000;
		boolean isReadAll = (head <= 0) && (tail <= 0);
		
		StringBuilder result = new StringBuilder();
		try {
			long starttime = System.currentTimeMillis();
			
			int linesReadHead = 0;
			int skippedCount = 0;
			EvictingQueue<String> tailedLines = EvictingQueue.create(tail);

			while(checkKeepExecuting()) {
				Thread.sleep(20);
				
				//-----------------------------
				// Check Timeout
				if( (System.currentTimeMillis() - starttime) >= timeoutMillis ) {
					this.interrupt();
					break;
				}
				
				//----------------------------------
				// Read Head
				while(out.hasLine() && (isReadAll || linesReadHead < head ) ) {
					result.append(out.readLine()).append("\n");
					linesReadHead++;
				}
								
				//----------------------------------
				// Read Tail
				if(isReadAll || linesReadHead >= head) {
					
					while(out.hasLine() ) {
						tailedLines.add(out.readLine());
						if(tailedLines.size() >= tail) {
							skippedCount++;
						}
					}
				}
				
				if(exceptionDuringRun != null) { throw exceptionDuringRun; }
				
				if(Thread.interrupted()) { this.interrupt(); }

			}
			
			//----------------------------------
			// Add Skipped Count
			if( !isReadAll && addSkippedCount) {
				
				if(skippedCount > 1) {
					result.append("[... "+(skippedCount-1)+" lines skipped ...]\n");
				}
			}
			
			//----------------------------------
			// Read Tail
			while( ! tailedLines.isEmpty() ) {
				result.append(tailedLines.poll()).append("\n");
			}

		} catch (InterruptedException e) {
			this.interrupt();
		}
		
		return result.toString();

	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public String waitForCompletionAndReadOutput(long timeoutSeconds, int headLines, int tailLines) throws Exception {
		
		StringBuilder result = new StringBuilder();
		
		long timeoutMillis = timeoutSeconds * 1000;
		try {
			long starttime = System.currentTimeMillis();
			
			while(checkKeepExecuting()) {
				Thread.sleep(20);
				
				if( (System.currentTimeMillis() - starttime) >= timeoutMillis ) {
					this.interrupt();
					break;
				}
			}
						
			if(exceptionDuringRun != null) { throw exceptionDuringRun; }
			
			if(Thread.interrupted()) { this.interrupt(); }
			
		} catch (InterruptedException e) {
			this.interrupt();
		}
		
		return result.toString();

	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	@Override
	public void interrupt() {
		isInterrupted = true;
		super.interrupt();
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	@Override
	public void run() {
		
		isCompleted = false;
		exceptionDuringRun = null;
		
		processes = null;
		try {
			for(ArrayList<ProcessBuilder> pipeline : pipelines) {
				
				if( !checkKeepExecuting()) { break; }
				
				lastProcess = null;
				try {
					
					//------------------------------------
					// Start Pipeline
					processes = ProcessBuilder.startPipeline(pipeline); 
				
					//------------------------------------
					// Log FINER
					if(logger.isEnabledForLevel(Level.TRACE)) {
						for(Process p : processes) {
							logger.trace("Process Started: PID: "+ p.pid() +", INFO: "+p.info());
						}
					}
					
					//------------------------------------
					// Read output of last Process in pipeline
					lastProcess = processes.get(processes.size() - 1);				    
				    BufferedReader reader = new BufferedReader( new InputStreamReader(lastProcess.getInputStream()) );

				    //---------------------
				    // Read the Output
				    String line;
				    while((line = reader.readLine()) != null && checkKeepExecuting()) {
				    	out.write((line+"\n").getBytes());
				    }
				    
				}finally {
					kill();
				}
			}
		}catch(Exception e) {
			
			exceptionDuringRun = e;
		}finally {
			isCompleted = true;
		}
		
	}

	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	public void kill() {
		
		if(processes != null) {
			for(Process p : processes) {
				killProcessTree(p.toHandle());
			}
		}
		
		try {
			if(lastProcess != null) {
				lastProcess.getInputStream().close();
			}
		}catch(IOException e) {
			logger.warn("IOException while closing InputStream: "+e.getMessage());
		}
	}
	
	/***************************************************************************
	 * 
	 * 
	 ***************************************************************************/
	private void killProcessTree(ProcessHandle p) {
		
		logger.trace("Destroy Process with PID: "+p.pid()+", INFO: "+p.info());
		p.descendants().forEach( handle -> { killProcessTree(handle); });
		p.destroyForcibly();
	}
		
}


