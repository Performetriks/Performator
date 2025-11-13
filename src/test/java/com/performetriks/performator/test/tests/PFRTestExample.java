package com.performetriks.performator.test.tests;

import java.time.Duration;

import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.base.PFRTest;
import com.performetriks.performator.executors.PFRExecStandard;
import com.performetriks.performator.test.globals.Globals;
import com.performetriks.performator.test.usecase.UsecaseExample;
import com.performetriks.performator.test.usecase.UsecaseExampleSLA;

public class PFRTestExample extends PFRTest {

	public PFRTestExample() {
		super();
		
		Globals.commonInitialization();
		
		int percentage = 100;

		this.add(new PFRExecStandard(UsecaseExample.class, 50, 12000, 0, 5).percent(percentage) );
		
		this.add(new PFRExecStandard(UsecaseExampleSLA.class)
						.users(5)
						.execsHour(2000)
						.rampUp(2) 
						.offset(20)
						.percent(percentage)
					);
		
		this.maxDuration(Duration.ofSeconds(90));
		this.gracefulStop(Duration.ofSeconds(90));
		
	}

}
