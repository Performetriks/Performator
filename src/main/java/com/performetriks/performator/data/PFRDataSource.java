package com.performetriks.performator.data;

import java.util.ArrayList;
import java.util.Collections;

import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFR;
import com.xresch.hsr.base.HSR;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRDataSource {
	
	Logger logger = (Logger) LoggerFactory.getLogger(PFRDataSource.class.getName());
	
	private ArrayList<PFRDataRecord> data = new ArrayList<>();
	
	private String datasourceName; 
	private int lastIndex = 0;
	
	private AccessMode accessMode = AccessMode.SEQUENTIAL;
	private RetainMode retainMode = RetainMode.INFINITE;
	
	private Object SYNC_LOCK = new Object();
	
	/** Defines how the data should be accessed */
	public enum AccessMode {
		  /** Read the data in sequence. */
		  SEQUENTIAL
		  /** Read the data in random order. */
		, RANDOM
		  /** Shuffle the data once, then read it in sequence. */
		, SHUFFLE
		
	}
	
	/** Defines if a record should be used only once or multiple times */
	public enum RetainMode {
		  INFINITE
		, ONCE
	}
	
	/*****************************************************************
	 * Constructor
	 *****************************************************************/
	public PFRDataSource(String datasourceName) {
		this.datasourceName = datasourceName;
	}
	
	/*****************************************************************
	 * implement this to load the data and return it as data records.
	 * 
	 * @return list or records.
	 *****************************************************************/
	protected abstract ArrayList<PFRDataRecord> load();
	
	
	/*****************************************************************
	 * Prepares the data source for being used.
	 *****************************************************************/
	public PFRDataSource build() {
		
		//-----------------------------------
		// Load the data
		data = load();
		if(data == null) {
			data = new ArrayList<>();
		}
		
		//-----------------------------------
		// Check empty
		if(data.isEmpty()) {
			String message = "The data source "+datasourceName+" was empty on load.";
			logger.warn(message);
			HSR.addWarnMessage(message);
		}
		
		//-----------------------------------
		// Shuffle
		if(AccessMode.SHUFFLE == accessMode) {
			Collections.shuffle(data);
		}
		
		return this;
	}
	
	/*****************************************************************
	 * Return true if this data source still has data.
	 * Useful when using RetainMode.ONCE.
	 *****************************************************************/
	public boolean hasNext() {
		return data.size() > 0;
	}
	
	/*****************************************************************
	 * The internal method that load the data.
	 *****************************************************************/
	public PFRDataRecord next() {
		
		synchronized (SYNC_LOCK) {
			switch(accessMode) {
			case SEQUENTIAL:
			case SHUFFLE:
				return nextSequential();
				
			case RANDOM: 
				return nextRandom();
			
			// default should never be reached, except the developer messed up
			default:
				logger.error("Undefined AccessMode: "+accessMode, new Exception()); 
				return null;

			
			}
		}
		
	}
	
	/*****************************************************************
	 * The internal method that load the data.
	 *****************************************************************/
	private PFRDataRecord nextSequential() {
		
		switch(retainMode) {
			case INFINITE:
				PFRDataRecord record = data.get(lastIndex);
				lastIndex++;
				return record;
				
			case ONCE:
				return data.remove(lastIndex);
				
			// default should never be reached, except the developer messed up
			default:
				logger.error("Undefined RetainMode: "+retainMode, new Exception());
				return null;
		}

	}
	
	/*****************************************************************
	 * The internal method that load the data.
	 *****************************************************************/
	private PFRDataRecord nextRandom() {
		
		PFRDataRecord record = PFR.Random.fromArray(data);
		
		switch(retainMode) {
			case INFINITE:
				return record;
				
			case ONCE:
				data.remove(record);
				return record;
				
			// default should never be reached, except the developer messed up
			default:
				logger.error("Undefined RetainMode: "+retainMode, new Exception());
				return null;
		}

	}
	
	
	
	

}
