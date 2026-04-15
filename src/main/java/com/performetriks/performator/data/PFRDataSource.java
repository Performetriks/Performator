package com.performetriks.performator.data;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.performetriks.performator.base.PFRConfig;
import com.performetriks.performator.base.PFRCoordinator;
import com.xresch.xrutils.data.XRRecord;


/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRDataSource {
	
	Logger logger = LoggerFactory.getLogger(PFRDataSource.class.getName());
	
	// data source key plus source itself
	private static HashMap<String, PFRDataSource> registeredDataSources = new HashMap<>();
	
	protected String uniqueName; 
	private boolean isShared = false;
	
	private boolean isBuilt = false;
	
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
		&& isShared
		&& registeredDataSources.containsKey(uniqueName)) {
			logger.warn("Data Source with name '"+uniqueName+"' has been reset for all agents."
					+ "In case you want to use the source only locally, use the method yourSource.local() to define that it should not be shared between agents.");
		}
		
		registeredDataSources.put(uniqueName, this);
		
		isBuilt = true;
		
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
	 * This is the internal method that you implement so it 
	 * prepares the data source for being used.
	 * 
	 * @return instance for chaining
	 * 
	 *****************************************************************/
	protected abstract PFRDataSource buildSource();
	
	/*****************************************************************
	 * Return true if this data source still has data.
	 * Useful when using RetainMode.ONCE.
	 *****************************************************************/
	public abstract boolean hasNext();
	

	/*****************************************************************
	 * The method that will be called by the user to load the next
	 * data record.
	 *****************************************************************/
	protected abstract XRRecord nextInternal();
	
	
	/*****************************************************************
	 * Returns a clone of the data record. Clones are used to ensure
	 * data is not overwritten by multiple users using the same 
	 * data record as the same time.
	 * This allows you to add additional data to your record while
	 *  using it.
	 *****************************************************************/
	public XRRecord next() {
		
		if( ! isBuilt ) { logger.warn("The data source's .build() method was not called and it might not work correctly: "+this.getUniqueName() ); }
		
		XRRecord record;
		if(! isShared() 
		|| ! PFRCoordinator.isDataAgentConnected()
		){
			record = nextInternal();
		}else {
			record = PFRCoordinator.nextFromAgent(this);
		}
		
		return record != null ? record.clone() : null;
	}
	
	/*****************************************************************
	 * Returns the number of data records.
	 *****************************************************************/
	public abstract int size();
	
	/*****************************************************************
	 * Set the source to be shared.
	 * By default, a source is set not to be shared between agents.
	 * If you set it to shared, the data will be shared between agents.
	 * 
	 *****************************************************************/
	public PFRDataSource shared() {
		this.isShared = true;
		return this;
	}
	
	/*****************************************************************
	 * 
	 * @return true if shared, false otherwise
	 *****************************************************************/
	public boolean isShared() {
		return isShared;
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
