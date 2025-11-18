package com.performetriks.performator.base;

import com.performetriks.performator.data.PFRData;
import com.xresch.hsr.base.HSR;

/***************************************************************************
 * Main Performator class used for configuration and controlling the framework.
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFR {
	
	public class CSV extends HSR.CSV{}; 
	public class Data extends PFRData{}; 
	public class Files extends HSR.Files{}; 
	public class JSON extends HSR.JSON{}; 
	public class Math extends HSR.Math{}; 
	public class Random extends HSR.Random{}; 
	public class Text extends HSR.Text{}; 
	public class Time extends HSR.Time{}; 

}
