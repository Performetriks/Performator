package com.performetriks.performator.cli;

/**************************************************************************************************************
 * This class exists solely for the reason that certain things are hard to monitor without making 
 * programmatic backflips and splits.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 **************************************************************************************************************/

public abstract class PFRMonitor {

	private PFRMonitor parent = null;
	private PFRMonitor chained = null;
	
	private boolean chainedAsOrCondition = false;
	
	/******************************************************
	 * This method will return a boolean to indicate:
	 * - true: all is good / keep executing etc...
	 * - false: something is off / stop processing etc...
	 * 
	 * This method will always go up to the first monitor 
	 * in the chain of monitors and execute the check from there.
	 * 
	 * @return true if all is good, false if not
	 ******************************************************/
	public boolean check() {
		
		if(parent != null) {
			return parent.check();
		}else {
			return checkChained(chainedAsOrCondition);
		}
		
	}
	
	/******************************************************
	 * 
	 ******************************************************/
	private boolean checkChained(boolean useOrCondition) {
		
		if(chained == null) {
			return monitorCondition();
		}else {
			if(!useOrCondition) {
				return monitorCondition() && chained.checkChained(useOrCondition);
			}else {
				return monitorCondition() || chained.checkChained(useOrCondition);
			}
		}
	}
	
	/******************************************************
	 * Sets the parent in the chain.
	 ******************************************************/
	private void setParent(PFRMonitor parent) {
		this.parent = parent;
	}
	
	/******************************************************
	 * Gets the parent of this monitor in the chain.
	 * @return monitor or null
	 ******************************************************/
	public PFRMonitor getParent() {
		return this.parent;
	}
	
	/******************************************************
	 * Chains to monitor to the end of the chain of monitors.
	 * 
	 * All monitors will be evaluated until either one returns
	 * false or all monitors are true. This behaviour can be changed
	 * by calling the method chainUseORCondition().
	 * 
	 * @return CFWMonitor the instance the method was called on. 
	 * 
	 ******************************************************/
	public PFRMonitor chain(PFRMonitor monitorToAdd){

		if(chained == null) {
			chained = monitorToAdd;
			chained.setParent(this);
		}else {
			
			// prevents endless loop
			if(!chained.equals(monitorToAdd)) {
				chained.chain(monitorToAdd);
			}
			
		}
		
		return this;
		 
	}
	
	/******************************************************
	 * Will change the evaluation of the monitors from an
	 * AND-evaluation (all have to be true) to an 
	 * OR-evaluation (at least one has to be true)
	 * @param doUseOrCondition TODO
	 * 
	 * @return CFWMonitor the instance the method was called on. 
	 * 
	 ******************************************************/
	public PFRMonitor chainUseORCondition(boolean doUseOrCondition){
		chainedAsOrCondition = doUseOrCondition;
		
		if(parent != null) {
			parent.chainUseORCondition(doUseOrCondition);
		}else {
			chainedAsOrCondition = doUseOrCondition;
		}
		
		return this;
	}
	
	/******************************************************
	 * Implement this method to indicate:
	 * - true: all is good / keep executing etc...
	 * - false: something is off / stop processing etc...
	 *  
	 * @return true if all is good, false if not
	 ******************************************************/
	protected abstract boolean monitorCondition();
	
}
