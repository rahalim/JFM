package jfm.scenario;

import java.util.HashMap;

import jfm.network.Centroid;


public class ODscenario extends ODdata {
	private double multiplicationFactor;
	
	public ODscenario(String odFileName, HashMap<String, Centroid> countries,
			double multiplicationFactor) {
		super(odFileName, countries);
		this.multiplicationFactor = 1.0+multiplicationFactor;
	}

	@Override
	public double getFlow(Centroid origin, Centroid destination) {
		return super.getFlow(origin, destination) * this.multiplicationFactor;
	}

}
