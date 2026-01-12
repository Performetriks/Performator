package com.performetriks.performator.data;

import java.util.HashMap;

import org.slf4j.LoggerFactory;

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
	
	
	private static HashMap<String, PFRDataSource> registeredDataSources = new HashMap<>();
	
	private String datasourceName; 
	private boolean isLocal = false; // Make source shared between agents by default;
	
	protected AccessMode accessMode = AccessMode.SEQUENTIAL;
	protected RetainMode retainMode = RetainMode.INFINITE;
	

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
		  /** Use any data record an infinite amount of times. */
		  INFINITE
		  /** Use any data record once. */
		, ONCE
	}
	
	/*****************************************************************
	 * Constructor
	 *****************************************************************/
	public PFRDataSource(String datasourceName) {
		this.datasourceName = datasourceName;
	}
	
	/*****************************************************************
	 * Removes a registered data source.
	 * 
	 * @return the removed source 
	 *****************************************************************/
	public static PFRDataSource unregisterSource(String datasourceName) {
		return registeredDataSources.remove(datasourceName);
	}
	
	
	/*****************************************************************
	 * Removes all registered data sources.
	 * 
	 * @return the removed source 
	 *****************************************************************/
	public static void clearSources() {
		registeredDataSources.clear();
	}
	
	/*****************************************************************
	 * This method prepares the data source for being used.
	 * 
	 * @return instance for chaining
	 * 
	 *****************************************************************/
	public PFRDataSource build() {
		
		//------------------------------------
		// register Data Source
		if(!isLocal 
		&& registeredDataSources.containsKey(datasourceName)) {
			throw new RuntimeException("Data Source with name '"+datasourceName+"' has already been registered. Best thing to do is to rename the data source and make sure it is only loaded once.");
		}
		registeredDataSources.put(datasourceName, this);
		
		return buildSource();
		
	}
	
	/*****************************************************************
	 * This method prepares the data source for being used.
	 * 
	 * @return instance for chaining
	 * 
	 *****************************************************************/
	public abstract PFRDataSource buildSource();
	
	/*****************************************************************
	 * Return true if this data source still has data.
	 * Useful when using RetainMode.ONCE.
	 *****************************************************************/
	public abstract boolean hasNext();
	
	/*****************************************************************
	 * The method that will be called by the user to load the next
	 * data record.
	 *****************************************************************/
	public abstract PFRDataRecord next();
	
	/*****************************************************************
	 * Returns the number of data records.
	 *****************************************************************/
	public abstract int size();
	
	/*****************************************************************
	 * Set the access mode to SEQUENTIAL.
	 * Reads the data in sequence.
	 * @return instance for chaining
	 *****************************************************************/
	public PFRDataSource sequential() {
		this.accessMode = AccessMode.SEQUENTIAL;
		return this;
	}
	
	/*****************************************************************
	 * Set the access mode to RANDOM.
	 * Reads the data in random order.
	 * @return instance for chaining
	 *****************************************************************/
	public PFRDataSource random() {
		this.accessMode = AccessMode.RANDOM;
		return this;
	}
	
	/*****************************************************************
	 * Set the access mode to SHUFFLE.
	 * Shuffles the data once, then reads it in sequence.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRDataSource shuffle() {
		this.accessMode = AccessMode.SHUFFLE;
		return this;
	}
	
	
	/*****************************************************************
	 * Set the retain mode to INFINITE.
	 * Use any data record an infinite amount of times.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRDataSource infinite() {
		this.retainMode = RetainMode.INFINITE;
		return this;
	}
	
	/*****************************************************************
	 * Set the retain mode to ONCE.
	 * Use every data record exactly once.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRDataSource once() {
		this.retainMode = RetainMode.ONCE;
		return this;
	}
	
	
	/*****************************************************************
	 * Returns the name of the data source.
	 * 
	 * @return the retain mode
	 *****************************************************************/
	public String name() {
		return this.datasourceName;
	}
	
	/*****************************************************************
	 * Set the retain mode.
	 * 
	 * @return  instance for chaining
	 *****************************************************************/
	public PFRDataSource retainMode(RetainMode retainMode) {
		this.retainMode = retainMode;
		return this;
	}
	
	/*****************************************************************
	 * Returns the retain mode.
	 * 
	 * @return the retain mode
	 *****************************************************************/
	public RetainMode retainMode() {
		return this.retainMode;
	}
	
	/*****************************************************************
	 * Set the access mode.
	 * 
	 * @return  instance for chaining
	 *****************************************************************/
	public PFRDataSource accessMode(AccessMode accessMode) {
		this.accessMode = accessMode;
		return this;
	}
		
	/*****************************************************************
	 * Returns the access mode.
	 * 
	 * @return the access mode
	 *****************************************************************/
	public AccessMode accessMode() {
		return this.accessMode;
	}
	

	
	
	
	

}
