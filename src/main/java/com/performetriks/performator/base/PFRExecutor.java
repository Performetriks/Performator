package com.performetriks.performator.base;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public abstract class PFRExecutor {
	
	
	private PFRUsecase usecase;
	
	/*****************************************************************
	 * Clones this instance of the executor.
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRExecutor(PFRUsecase usecase){
		this.usecase = usecase;
	}
	
	/*****************************************************************
	 * 
	 * @return instance for chaining
	 *****************************************************************/
	public PFRUsecase usecase(){
		return usecase;
	}

	
}
