package com.performetriks.performator.test.usecase;

import java.math.BigDecimal;

import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRSLA;
import com.xresch.hsr.stats.HSRExpression.Operator;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;
import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;

public class UsecaseExample extends PFRUsecase {

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void initializeUser(PFRContext context) {
		// TODO Auto-generated method stub
		// nothing todo
	}

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void execute(PFRContext context) throws Throwable {
		//-------------------------------
		// 
		HSR.start("000_Open_Homepage");
			Thread.sleep(HSR.Random.integer(50, 200));
		HSR.end();
		
		//-------------------------------
		// 
		HSR.start("010_Login");
			Thread.sleep(HSR.Random.integer(100, 300));
		HSR.end();
		
		HSR.startGroup("015_MyGroup");
			HSR.startGroup("017_MySubGroup");
				//-------------------------------
				// 
				HSR.start("020_Execute_Search");
					Thread.sleep(HSR.Random.integer(100, 5000));
				HSR.end();
				
				//-------------------------------
				// 
				HSR.start("030_Click_Result");
					Thread.sleep(HSR.Random.integer(100, 200));
				HSR.end();
				
				//-------------------------------
				// 
				HSR.start("040_SometimesFails");
					Thread.sleep(HSR.Random.integer(50, 100));
					
					boolean isSuccess = HSR.Random.bool();
					if(!isSuccess) {
						HSR.addErrorMessage("Exception Occured: Figure it out!");
						HSR.addException(new Exception("This is an exception."));
					}
				HSR.end(isSuccess);
				
				//-------------------------------
				// 
				HSR.start("050_RandomStatusAndCode");
					Thread.sleep(HSR.Random.integer(10, 200));
					String code = HSR.Random.fromArray(new String[] {"200", "200", "200", "200", "200", "401", "500"});
				HSR.end(HSR.Random.fromArray(HSRRecordStatus.values()), code);
				
				//-------------------------------
				// 
				HSR.assertEquals("A"
						, HSR.Random.fromArray(new String[] {"A", "A", "A", "B"})
						,  "060_Assert_ContainsA");

			HSR.end();
		HSR.end();
		
		//-------------------------------
		// 
		HSR.start("070_CustomValues");
			Thread.sleep(HSR.Random.integer(100, 300));
			
			// Add a Gauge, will be averaged in aggregation
			HSR.addGauge("070.1 Gauge: SessionCount", HSR.Random.bigDecimal(80, 250));
			
			// Add a Count, will be summed up in aggregation 
			HSR.addCount("070.2 Count: TiramisusEaten", HSR.Random.bigDecimal(0, 100));
			HSR.addInfoMessage(HSR.Random.from("Valeria", "Roberta", "Ariella") + " has eaten the Tiramisu!");
			
			// Add a Metric, will calculate statistical values for it
			HSR.addMetric("070.3 Metric: TimeWalked", HSR.Random.bigDecimal(100, 300));
			
			// Add a Ranged Metric
			// simulate a correlation between count and duration
			int multiplier = HSR.Random.integer(0, 10);
			int count = multiplier * HSR.Random.integer(1, 900);
			int duration = multiplier * HSR.Random.integer(10, 1000);
			HSR.addMetricRanged("070.4 TableLoadTime", new BigDecimal(duration), count, 50);
			
		HSR.end(HSR.Random.fromArray(HSRRecordStatus.values()));
		
		//-------------------------------
		// Keep it open to test HSR. endAllOpen()
		HSR.start("999 The Unending Item");
			Thread.sleep(HSR.Random.integer(15, 115));

	}

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void terminate(PFRContext context) {
		// nothing todo
	}

}
