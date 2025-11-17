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
public abstract class PFRDataSourceStatic extends PFRDataSource {
	
	Logger logger = (Logger) LoggerFactory.getLogger(PFRDataSourceStatic.class.getName());
	
	private ArrayList<PFRDataRecord> data = new ArrayList<>();
	
	private int lastIndex = 0;
	
	private Object SYNC_LOCK = new Object();
		
	/*****************************************************************
	 * This method will load the data and return it as data records.
	 * 
	 * @return list or records.
	 *****************************************************************/
	protected abstract ArrayList<PFRDataRecord> load();
	
	/*****************************************************************
	 * Constructor
	 *****************************************************************/
	public PFRDataSourceStatic(String datasourceName) {
		super(datasourceName);
	}
	
	
	/*****************************************************************
	 * Prepares the data source for being used.
	 *****************************************************************/
	public PFRDataSourceStatic build() {
		
		//-----------------------------------
		// Load the data
		data = load();
		if(data == null) {
			data = new ArrayList<>();
		}
		
		//-----------------------------------
		// Check empty
		if(data.isEmpty()) {
			String message = "The data source "+name()+" was empty on load.";
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
	 * 
	 *****************************************************************/
	public PFRDataRecord next() {
		
		synchronized (SYNC_LOCK) {
			
			if(!hasNext()) { return null; }
			
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
