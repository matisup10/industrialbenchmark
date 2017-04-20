/*
Copyright 2016 Siemens AG.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.siemens.industrialbenchmark.externaldrivers.setpointgen;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.math3.random.RandomDataGenerator;

import com.google.common.base.Preconditions;
import com.siemens.industrialbenchmark.datavector.DataVectorImpl;
import com.siemens.industrialbenchmark.properties.PropertiesException;
import com.siemens.industrialbenchmark.properties.PropertiesUtil;
import com.siemens.industrialbenchmark.util.PlotCurve;
import com.siemens.rl.interfaces.DataVector;
import com.siemens.rl.interfaces.ExternalDriver;

/**
 * The seedable setpoint generator.
 *
 * @author Siegmund Duell, Michel Tokic
 */
public class SetPointGenerator implements ExternalDriver {

	private final float SETPOINT_STEP_SIZE;
	private final float MAX_CHANGE_RATE_PER_STEP_SETPOINT;
	private final int MAX_SEQUENCE_LENGTH;
	private final float MINSETPOINT;
	private final float MAXSETPOINT;

	private int mCurrentSteps;
	private int mLastSequenceSteps;
	private double mChangeRatePerStep;
	private boolean mIsStationary;
	private double mSetPoint;

	private RandomDataGenerator mRandom = new RandomDataGenerator();

	/**
	 * Constructor with given seed and properties file
	 * @param seed The seed for the random number generator
	 * @param aProperties The properties file to parse
	 * @throws PropertiesException if property values are badly formatted
	 */
	public SetPointGenerator(final long seed, final Properties aProperties)
			throws PropertiesException
	{
		this.mIsStationary = aProperties.containsKey("STATIONARY_SETPOINT");
		if (mIsStationary) {
			this.mSetPoint = PropertiesUtil.getFloat(aProperties, "STATIONARY_SETPOINT", true);
			Preconditions.checkArgument(mSetPoint >= 0.0f && mSetPoint <= 100.0f, "setpoint must be in range [0, 100]");
		}
		this.MAX_CHANGE_RATE_PER_STEP_SETPOINT = PropertiesUtil.getFloat(aProperties, "MAX_CHANGE_RATE_PER_STEP_SETPOINT", true);
		this.MAX_SEQUENCE_LENGTH = PropertiesUtil.getInt(aProperties, "MAX_SEQUENCE_LENGTH", true);
		this.MINSETPOINT = PropertiesUtil.getFloat(aProperties, "SetPoint_MIN", true);
		this.MAXSETPOINT = PropertiesUtil.getFloat(aProperties, "SetPoint_MAX", true);
		this.SETPOINT_STEP_SIZE = PropertiesUtil.getFloat(aProperties, "SETPOINT_STEP_SIZE", true);

		this.mRandom = new RandomDataGenerator();
		this.mRandom.reSeed(seed);
		defineNewSequence();
	}

	/**
	 * Constructs a new object using the current time in milliseconds as a seed.
	 * @param aProperties The properties file to parse
	 * @throws PropertiesException if property values are badly formatted
	 */
	public SetPointGenerator(final Properties aProperties) throws PropertiesException {
		this(System.currentTimeMillis(), aProperties);
	}

	/**
	 * @return the current steps
	 */
	public int getCurrentSteps() {
		return this.mCurrentSteps;
	}

	/**
	 * @return the change rate per step
	 */
	public double getChangeRatePerStep() {
		return this.mChangeRatePerStep;
	}

	/**
	 * Sets the current state of the setpoint generation engine.
	 * @param setpoint see {@link #getSetPoint()}
	 * @param currentSteps see {@link #getCurrentSteps()}
	 * @param lastSequenceSteps see {@link #getLastSequenceSteps()}
	 * @param changeRatePerStep see {@link #getChangeRatePerStep()}
	 */
	public void setState(final double setpoint, final int currentSteps, final int lastSequenceSteps, final double changeRatePerStep) {
		this.mSetPoint = setpoint;
		this.mCurrentSteps = currentSteps;
		this.mLastSequenceSteps = lastSequenceSteps;
		this.mChangeRatePerStep = changeRatePerStep;
	}

	/**
	 * returns the current setpoint
	 * @return the last sequence steps
	 */
	public double getSetPoint() {
		return this.mSetPoint;
	}

	/**
	 * returns the last sequence steps
	 * @return the last sequence steps
	 */
	public int getLastSequenceSteps() {
		return this.mLastSequenceSteps;
	}

	/**
	 * Returns the next setpoint and on the internal memorized old setpoint
	 * @return the next setpoint
	 */
	public double step() {
		final double newSetPoint = step(mSetPoint);
		mSetPoint = newSetPoint;
		return newSetPoint;
	}

	/**
	 * Returns the next setpoint
	 * @param aSetPoint
	 * @return
	 */
	private double step(final double aSetPoint) {
		if (mIsStationary) {
			return aSetPoint;
		}

		if (mCurrentSteps >= mLastSequenceSteps) {
			defineNewSequence();
		}

		mCurrentSteps++;
		double setpointLevel = aSetPoint + mChangeRatePerStep * SETPOINT_STEP_SIZE;

		if (setpointLevel > MAXSETPOINT) {
			setpointLevel = MAXSETPOINT;
			if (mRandom.nextBinomial(1, 0.5) == 1) {
				mChangeRatePerStep *= (-1);
			}
		}
		if (setpointLevel < MINSETPOINT) {
			setpointLevel = MINSETPOINT;
			if (mRandom.nextBinomial(1, 0.5) == 1) {
				mChangeRatePerStep *= (-1);
			}
		}

		assert setpointLevel <= MAXSETPOINT;
		return setpointLevel;
	}

	/**
	 * Defines a new setpoint trajectory sequence
	 */
	private void defineNewSequence() {
		//mLastSequenceSteps = mRandom.nextIntFromTo(0, MAX_SEQUENCE_LENGTH) + 1;
		mLastSequenceSteps = mRandom.nextInt(1, MAX_SEQUENCE_LENGTH);
		mCurrentSteps = 0;
		mChangeRatePerStep = mRandom.nextUniform(0, 1) * MAX_CHANGE_RATE_PER_STEP_SETPOINT;
		final double r = mRandom.nextUniform(0, 1);
		if (r < 0.45f) {
			mChangeRatePerStep *= (-1);
		}
		if (r > 0.9f) {
			mChangeRatePerStep = 0;
		}
	}

	/**
	 * Plots a setpoint trajectory for 10000 points.
	 *
	 * @param args command-line arguments
	 * @throws IOException when there is an error reading the configuration file
	 * @throws PropertiesException if the configuration file is badly formatted
	 */
	public static void main(final String[] args) throws IOException, PropertiesException {

		final int episodeLength = 10000;
		final double[] data = new double[episodeLength];

		final Properties props = PropertiesUtil.setpointProperties(new File("src/main/resources/sim.properties"));

		final SetPointGenerator lg = new SetPointGenerator(props);

		for (int i = 0; i < episodeLength; i++) {
			data[i] = lg.step();
		}

		PlotCurve.plot("SetPoint Trajectory", "Time", "SetPoint [%]", data);
	}

	@Override
	public void setSeed(final long seed) {
		this.mRandom.reSeed(seed);
	}

	@Override
	public void filter(final DataVector state) {
		state.setValue(SetPointGeneratorStateDescription.SET_POINT, step());
		state.setValue(SetPointGeneratorStateDescription.SET_POINT_CHANGE_RATE_PER_STEP, mChangeRatePerStep);
		state.setValue(SetPointGeneratorStateDescription.SET_POINT_CURRENT_STEPS, mCurrentSteps);
		state.setValue(SetPointGeneratorStateDescription.SET_POINT_LAST_SEQUENCE_STEPS, mLastSequenceSteps);
	}

	@Override
	public void setConfiguration(final DataVector state) {
		this.mSetPoint = state.getValue(SetPointGeneratorStateDescription.SET_POINT);
		this.mChangeRatePerStep = state.getValue(SetPointGeneratorStateDescription.SET_POINT_CHANGE_RATE_PER_STEP);
		this.mCurrentSteps = state.getValue(SetPointGeneratorStateDescription.SET_POINT_CURRENT_STEPS).intValue();
		this.mLastSequenceSteps = state.getValue(SetPointGeneratorStateDescription.SET_POINT_LAST_SEQUENCE_STEPS).intValue();
	}

	@Override
	public DataVector getState() {
		final DataVectorImpl s = new DataVectorImpl(new SetPointGeneratorStateDescription());
		s.setValue(SetPointGeneratorStateDescription.SET_POINT, mSetPoint);
		s.setValue(SetPointGeneratorStateDescription.SET_POINT_CHANGE_RATE_PER_STEP, mChangeRatePerStep);
		s.setValue(SetPointGeneratorStateDescription.SET_POINT_CURRENT_STEPS, mCurrentSteps);
		s.setValue(SetPointGeneratorStateDescription.SET_POINT_LAST_SEQUENCE_STEPS, mLastSequenceSteps);

		return s;
	}
}

