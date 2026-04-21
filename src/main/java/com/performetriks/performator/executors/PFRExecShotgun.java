package com.performetriks.performator.executors;

import java.util.concurrent.TimeUnit;


import com.performetriks.performator.base.PFRUsecase;




/***************************************************************************
 *
 * PFRExecShotgun
 *
 * Executes N users at the same time in an interval based on a pacing time.
 *
 * Supports:
 *  - Offset
 *  - Percent load
 *
 * Pattern:
 *
 * > = offset
 * # = execution
 * - = waiting
 * | = pacing
 *
 * User 1: >>>>|###-----------------|########------------|
 * User 2: >>>>|#####---------------|###-----------------|
 *
 * Copyright Owner: Performetriks
 * License: MIT License
 *
 ***************************************************************************/
public class PFRExecShotgun extends PFRExecStandard {

	private int offsetSeconds = 0;
	private int percent = 100;
	private int originalUsers;

	/***************************************************************************
	 * Constructor
	 ***************************************************************************/
	public PFRExecShotgun(
			Class<? extends PFRUsecase> usecase,
			int users,
			int pacingSeconds) {

		super(
				usecase,
				users,
				(int) TimeUnit.SECONDS.toMillis(pacingSeconds),
				0,
				-1
		);

		this.originalUsers = users;
	}

	/***************************************************************************
	 * Offset in seconds
	 ***************************************************************************/
	public PFRExecShotgun offset(int offsetSeconds) {
		this.offsetSeconds = offsetSeconds;


		return this;
	}

	/***************************************************************************
	 * Percent load
	 ***************************************************************************/
	public PFRExecShotgun percent(int percent) {
		this.percent = percent;
		return this;
	}





}