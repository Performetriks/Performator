package com.performetriks.performator.test;

import org.junit.jupiter.api.Test;

import com.performetriks.performator.base.PFRCoordinator;
import com.performetriks.performator.test.tests.PFRTestExample;

public class TestCoordinator {


	
	/*****************************************************************
	 * 
	 *****************************************************************/
	@Test
	void testCoordinator() {
		
		PFRCoordinator.startTest(PFRTestExample.class.getName());
		
	}
	

}