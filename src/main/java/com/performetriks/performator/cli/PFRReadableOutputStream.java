package com.performetriks.performator.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

/**************************************************************************************************************
 * A class that allows to read an output stream line by line or all lines at once.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 **************************************************************************************************************/
public class PFRReadableOutputStream extends OutputStream{

	protected boolean isClosed = false;
	protected LinkedBlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();
	
	//contains currentLine
	protected LinkedBlockingQueue<Integer> byteQueue = new LinkedBlockingQueue<>();
	
	protected int newline = (int)'\n';
	
	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void write(int b) throws IOException {
		if(b != newline) {
			byteQueue.add(b);
		}else {
			StringBuilder line = new StringBuilder();
			while( !byteQueue.isEmpty() ) {
				line.append( (char)((int)byteQueue.poll()) );
			}
			lineQueue.add(line.toString());
			
		}
	}
		
	/************************************************************************
	 * 
	 ************************************************************************/
	public boolean hasLine() {
		return ! lineQueue.isEmpty();
	}
	
	/************************************************************************
	 * Reads a line.
	 ************************************************************************/
	public String readLine() {
		return lineQueue.poll();
	}
	
	
	/************************************************************************
	 * Reads a specific amount of head and tail.
	 * Method should be called after the OutputStream has received all data.
	 * If both params are <= 0 all lines will be read.
	 * 
	 * @param head number of lines to read from the start of the output
	 * @param tail number of lines to read at the end of the output
	 * @param addSkippedCount add number of lines to the output.
	 * 
	 ************************************************************************/
	public String readHeadAndTail(int head, int tail, boolean addSkippedCount) {
		
		if(head <= 0 && tail <= 0) { return readAll(); }
		
		StringBuilder builder = new StringBuilder();
		int lineCount = lineQueue.size();
		//----------------------------------
		// Read Head
		int i = 0;
		while(! lineQueue.isEmpty() && i < head) {
			builder.append(lineQueue.poll()).append("\n");
			i++;
		}
		
		//----------------------------------
		// Skipping Lines
		int skippedCount = lineCount - head - tail;
		
		int s = 0;
		while( ! lineQueue.isEmpty() && s < skippedCount ) {
			lineQueue.poll();
			s++;
		}
		
		//----------------------------------
		// Add Skipped Count
		if(addSkippedCount) {
			
			if(skippedCount > 0) {
				builder.append("[... "+skippedCount+" lines skipped ...]\n");
			}
		}
		
		//----------------------------------
		// Read Tail
		i = 0;
		while(! lineQueue.isEmpty() && i < tail) {
			builder.append(lineQueue.poll()).append("\n");
			i++;
		}
		
		return builder.toString();
	}
	/************************************************************************
	 * Reads all lines.
	 * Method should be called after the OutputStream has received all data.
	 ************************************************************************/
	public String readAll() {
		
		StringBuilder builder = new StringBuilder();
		while(! lineQueue.isEmpty()) {
			builder.append(lineQueue.poll()).append("\n");
		}
		
		return builder.toString();
	}
	
	/************************************************************************
	 * 
	 ************************************************************************/
	public boolean isClosed() {
		return isClosed;
	}
	
	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
    public void close() throws IOException {
		super.close();
		this.isClosed = true;
    }

}
