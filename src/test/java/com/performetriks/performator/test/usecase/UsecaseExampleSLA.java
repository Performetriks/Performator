package com.performetriks.performator.test.usecase;

import java.math.BigDecimal;

import com.performetriks.performator.base.PFRUsecase;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRSLA;
import com.xresch.hsr.stats.HSRExpression.Operator;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;
import com.xresch.hsr.stats.HSRRecordStats.HSRMetric;

public class UsecaseExampleSLA extends PFRUsecase {

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
	public void initializeUser() {
		// TODO Auto-generated method stub
		// nothing todo
	}

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void execute() throws Throwable {

		HSR.start("010_SLA_P90-NOK", SLA_P90_LTE_100MS);
			Thread.sleep(HSR.Random.integer(80, 120));
		HSR.end();
		
		HSR.start("020_SLA_P90-OK", SLA_P90_LTE_100MS);
			Thread.sleep(HSR.Random.integer(50, 100));
		HSR.end();
		
		HSR.start("030_SLA_P90_AND_AVG-NOK-P90", SLA_P90_AND_AVG);
			Thread.sleep(HSR.Random.fromInts(10,10,10, 10, 10, 101));
		HSR.end();
		
		HSR.start("040_SLA_P90_AND_AVG-NOK-AVG", SLA_P90_AND_AVG);
			Thread.sleep(HSR.Random.fromInts(50,50,50, 50, 50, 90));
		HSR.end();
		
		HSR.start("050_SLA_P90_AND_AVG-OK", SLA_P90_AND_AVG);
			Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 90));
		HSR.end();
		
		HSR.start("060_SLA_AVG_OR_P90-OK-ByAvg", SLA_AVG_OR_P90);
			Thread.sleep(HSR.Random.fromInts(5, 10, 20, 30, 40, 110));
		HSR.end();
		
		HSR.start("070_SLA_AVG_OR_P90-OK-ByP90", SLA_AVG_OR_P90);
			Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 90));
		HSR.end();
		
		HSR.start("080_SLA_AVG_OR_P90-NOK", SLA_AVG_OR_P90);
			Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
		HSR.end();
		
		HSR.start("090_SLA_FAILS_LT_10-OK", SLA_FAILRATE_LT_10);
			Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
		HSR.end( (HSR.Random.integer(0, 100) > 5) ? true : false );
		
		HSR.start("100_SLA_FAILS_LT_10-NOK", SLA_FAILRATE_LT_10);
			Thread.sleep(HSR.Random.fromInts(60, 60, 60, 60, 60, 110));
		HSR.end( (HSR.Random.integer(0, 100) > 20) ? true : false );

	}

	/************************************************************************
	 * 
	 ************************************************************************/
	@Override
	public void terminate() {
		// nothing todo
	}

}
