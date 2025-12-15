package com.performetriks.performator.base;

/***************************************************************************
 * Interface to register custom execution modes.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public interface PFRCustomMode {
	
	/****************************************************************************
	 * Return the unique name of the mode.
	 * This will be what you use in the command line args like -Dpfr_mode={name}
	 ****************************************************************************/
	public abstract String getUniqueName();
	
	/****************************************************************************
	 * Execute whatever you want to execute.
	 ****************************************************************************/
	public abstract void execute();
}
