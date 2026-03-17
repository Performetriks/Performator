package com.performetriks.performator.data;

import java.util.HashMap;

import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.performetriks.performator.base.PFRConfig;

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
	
	// data source key plus source itself
	private static HashMap<String, PFRDataSource> registeredDataSources = new HashMap<>();
	
	protected String uniqueName; 
	private boolean isLocal = false;
	
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
	public PFRDataSource() {
	}
	
	/*****************************************************************
	 * Constructor
	 *****************************************************************/
	public PFRDataSource(String datasourceName) {
		this.uniqueName = datasourceName;
	}
	
	/*****************************************************************
	 * Removes a registered data source.
	 * 
	 * @param datasourceName the unique name of the source
	 * @return the removed source 
	 *****************************************************************/
	public static PFRDataSource unregisterSource(String datasourceName) {
		return registeredDataSources.remove(datasourceName);
	}
	
	/*****************************************************************
	 * Gets the source with the specified name from the registry.
	 * @param datasourceName the unique name of the source
	 * @return the removed source 
	 *****************************************************************/
	public static PFRDataSource getSource(String datasourceName) {
		return registeredDataSources.get(datasourceName);
	}
	
	/*****************************************************************
	 * Checks if the registry contains the source with the given
	 * unique name.
	 * 
	 * @param datasourceName the unique name of the source
	 * @return true if registered, false otherwise 
	 *****************************************************************/
	public static boolean hasSource(String datasourceName) {
		return registeredDataSources.containsKey(datasourceName);
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
		
		String uniqueName = getUniqueName();
		//------------------------------------
		// register Data Source
		if( PFRConfig.hasAgents()
		&& ! isLocal
		&& registeredDataSources.containsKey(uniqueName)) {
			logger.warn("Data Source with name '"+uniqueName+"' has been reset for all agents."
					+ "In case you want to use the source only locally, use the method yourSource.local() to define that it should not be shared between agents.");
		}
		
		registeredDataSources.put(uniqueName, this);
		
		return buildSource();
		
	}
	
	/*****************************************************************
	 * This method should return a unique identifier for your data source.
	 * This is either the custom data source name, or a combination of
	 * values that uniquely identify the source.
	 * 
	 * @return String Datasource unique name
	 * 
	 *****************************************************************/
	public abstract String getUniqueName();
	
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
	protected abstract PFRDataRecord nextInternal();
	
	/*****************************************************************
	 * Returns a clone of the data record. Clones are used to ensure
	 * data is not overwritten by multiple users using the same 
	 * data record as the same time.
	 * This allows you to add additional data to your record while
	 *  using it.
	 *****************************************************************/
	public PFRDataRecord next() {
		
		return nextInternal().clone();
	}
	
	/*****************************************************************
	 * Returns the number of data records.
	 *****************************************************************/
	public abstract int size();
	
	/*****************************************************************
	 * Set the source to be local.
	 * By default, a source is set to be shared between agents.
	 * If you set it to local, the data will not be shared between agents.
	 * 
	 *****************************************************************/
	public PFRDataSource local() {
		this.isLocal = true;
		return this;
	}
	
	/*****************************************************************
	 * 
	 * @return true if local, false otherwise
	 *****************************************************************/
	public boolean isLocal() {
		return isLocal;
	}
	
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
		return this.uniqueName;
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
