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

	private static final HSRSLA SLA_P90_LTE_100MS = new HSRSLA(HSRMetric.p90, Operator.LTE, 100); 
	
	private static final HSRSLA SLA_P90_AND_AVG = new HSRSLA(HSRMetric.p90, Operator.LTE, 100)
															.and(HSRMetric.avg, Operator.LTE, 50); 
	
	private static final HSRSLA SLA_AVG_OR_P90 = new HSRSLA(HSRMetric.avg, Operator.LTE, 50)
														.or(HSRMetric.p90, Operator.LTE, 100); 
	
	private static final HSRSLA SLA_FAILRATE_LT_10 = new HSRSLA(HSRMetric.failrate, Operator.LT, 10); 
	
	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void initialize(PFRContext context) {
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
		// 
		HSR.startGroup("075 ServiceLevelAgreements");
			
			HSR.start("080_SLA_P90-NOK", SLA_P90_LTE_100MS);
				Thread.sleep(HSR.Random.integer(80, 120));
			HSR.end();
			
			HSR.start("085_SLA_P90-OK", SLA_P90_LTE_100MS);
				Thread.sleep(HSR.Random.integer(50, 100));
			HSR.end();
			
			HSR.start("090_SLA_P90_AND_AVG-NOK-P90", SLA_P90_AND_AVG);
				Thread.sleep(HSR.Random.fromInts(10,10,10, 10, 10, 101));
			HSR.end();
			
			HSR.start("100_SLA_P90_AND_AVG-NOK-AVG", SLA_P90_AND_AVG);
				Thread.sleep(HSR.Random.fromInts(50,50,50, 50, 50, 90));
			HSR.end();
			
			HSR.start("110_SLA_P90_AND_AVG-OK", SLA_P90_AND_AVG);
				Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 90));
			HSR.end();
			
			HSR.start("120_SLA_AVG_OR_P90-OK-ByAvg", SLA_AVG_OR_P90);
				Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 110));
			HSR.end();
			
			HSR.start("130_SLA_AVG_OR_P90-OK-ByP90", SLA_AVG_OR_P90);
				Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 90));
			HSR.end();
			
			HSR.start("140_SLA_AVG_OR_P90-NOK", SLA_AVG_OR_P90);
				Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
			HSR.end();
			
			HSR.start("150_SLA_FAILS_LT_10-OK", SLA_FAILRATE_LT_10);
				Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
			HSR.end( (HSR.Random.integer(0, 100) > 5) ? true : false );
			
			HSR.start("160_SLA_FAILS_LT_10-NOK", SLA_FAILRATE_LT_10);
				Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
			HSR.end( (HSR.Random.integer(0, 100) > 20) ? true : false );
		
		HSR.end( ); // Group SLA
		
		//-------------------------------
		// Keep it open to test HSR. endAllOpen()
		HSR.start("999 The Unending Item");
			Thread.sleep(HSR.Random.integer(15, 115));
			
		//-------------------------------
		// Make sure everything's closed
		HSR.endAllOpen(HSRRecordStatus.Aborted);

	}

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void terminate(PFRContext context) {
		// nothing todo
	}

}
