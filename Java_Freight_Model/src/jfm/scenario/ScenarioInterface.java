package jfm.scenario;

import java.util.Set;

import jfm.network.Centroid;
import jfm.network.MultiModalNetworkCostNoSQL;



public interface ScenarioInterface {

	/**
	 * make any scenario related modifications to the maritime network.
	 * This method is called immediately after creating the maritime network.
	 * @param marNetwork
	 */
	public void modifyMaritimeNetwork(MultiModalNetworkCostNoSQL marNetwork);
	
	/**
	 * make any scenario related modifications to the service network.
	 * This method is called immediately after creating the service network
	 * @param serNetwork
	 */
//	public void modifyServiceNetwork(ServiceNetwork serNetwork);
	
	/**
	 * get the flow between a given OD pair
	 * @param origin
	 * @param destination
	 * @return the flow between the origin and destination
	 */
	public double getFlow(Centroid origin, Centroid destination);
	
	/**
	 * get all the origin countries
	 * @return a set of all origins
	 */
	public Set<Centroid> getOrigins();
	
	/**
	 * get all the destinations for a given origin
	 * @param origin
	 * @return a set of all destinations for the specified origin
	 */
	public Set<Centroid> getDestinations(Centroid origin);
	
}
