package com.performetriks.performator.test.tests;

import java.time.Duration;

import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.executors.PFRExecutorStandard;
import com.performetriks.performator.test.globals.Globals;
import com.performetriks.performator.test.usecase.UsecaseExample;

public class PFRTestExample extends PFRTest {

	public PFRTestExample(PFRContext context) {
		super(context);
		
		Globals.commonInitialization();
		
		int multiplier = 3;
		int users = 10 * multiplier;
		int executionsPerHour = 1000 * multiplier;
		
		this.add(new PFRExecutorStandard(new UsecaseExample(), users, executionsPerHour, 0, 2) );
		
		this.maxDuration(Duration.ofSeconds(90));
		this.gracefulStop(Duration.ofSeconds(90));
		
	}

}
