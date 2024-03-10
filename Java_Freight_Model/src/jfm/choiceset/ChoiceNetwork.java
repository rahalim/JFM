package jfm.choiceset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import edu.uci.ics.jung.graph.util.Pair;
import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.AbstractServiceEdge;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.network.PortNode;
import jfm.scenario.ODdata;
import jfm.commodities.Commodity;
import jfm.models.Logit;


public class ChoiceNetwork  {

	private HashMap<Centroid, HashMap<Centroid, ChoiceSet>> choiceSetRoad;
	private HashMap<Centroid, HashMap<Centroid, ChoiceSet>> choiceSetRail;
	private Collection<Centroid> centroids;
	private HashMap<Integer,Centroid> centroidMap;
	private ODdata OD;
	private int mode;
	private int commodityIndex =0;
	private MultiModalNetworkCostNoSQL network;
	
	//constructor for international freight model without service network
	public ChoiceNetwork(MultiModalNetworkCostNoSQL freightNetwork, int mode) {

		this.network=freightNetwork;
		this.centroids = freightNetwork.getCentroids().values();
		this.centroidMap=freightNetwork.getCentroids();
		this.OD = freightNetwork.getBaseOD();	
		this.mode=mode;
		if(mode==0)
		{
			choiceSetRail= new HashMap<Centroid, HashMap<Centroid,ChoiceSet>>();
		}
		if(mode==1|| mode==3 ||mode==4)
		{
			choiceSetRoad= new HashMap<Centroid, HashMap<Centroid,ChoiceSet>>();
		}
		
		for (Centroid origin : centroids) {
			for (Centroid destination : centroids) {
				if(origin==destination){
					continue;
				}
//				System.out.println("from and to: "+origin.getId()+","+destination.getId());
				ChoiceSet choiceSet = this.buildChoiceSet(origin, destination, freightNetwork);
				this.addChoiceSet(origin,destination, choiceSet, mode);
			}
		}
	}
	
	//constructor for international freight model without service network
	//followed by assignment
	public ChoiceNetwork(MultiModalNetworkCostNoSQL freightNetwork,int mode, boolean direct) {	
		this.mode=mode;
		Collection<Centroid> centroids = freightNetwork.getCentroids().values();
		ArrayList<Commodity> commodities= freightNetwork.getCommodities();
		int commoditySize=commodities.size();

		for (Centroid origin : centroids) {
			for (Centroid destination : centroids) {
				if(origin==destination){
					continue;
				}

				int fromID =origin.getId();
				int toID =destination.getId();
				ChoiceSet choiceSet = this.buildChoiceSet(origin, destination, freightNetwork);
				
				for (int i=0; i<commoditySize; i++)
				{
					double flow =freightNetwork.getBaseOD().getWeightODByModes(fromID, toID, mode)[i];
					Logit logit = new Logit(choiceSet);
					logit.direcLogit(origin,destination, flow, commodityIndex, mode);
				}
			}
		}
	}

	/**
	 * @param origin
	 * @param destination
	 * @param choiceSet
	 */
	private void addChoiceSet(Centroid origin, Centroid destination, 
			ChoiceSet choiceSet, int mode)
	{
		if(mode==0)
		{
			if(this.choiceSetRail.containsKey(origin)==false){
				// the origin does not exist yet, so lets put the origin
				// and the associated HashMap with destinations in it
				this.choiceSetRail.put(origin, new HashMap<Centroid, ChoiceSet>());
			}		
			this.choiceSetRail.get(origin).put(destination, choiceSet);
		}
		
		if (mode==1|| mode==3||mode==4)
		{
			if(this.choiceSetRoad.containsKey(origin)==false){
				// the origin does not exist yet, so lets put the origin
				// and the associated HashMap with destinations in it
				this.choiceSetRoad.put(origin, new HashMap<Centroid, ChoiceSet>());
			}		
			this.choiceSetRoad.get(origin).put(destination, choiceSet);
		}
	}

	private ChoiceSet buildChoiceSet(Centroid origin, 
			Centroid destination, MultiModalNetworkCostNoSQL network){
				
		//the path is changed to abstract edge because we don't have service line here
		LinkedList<AbstractEdge> path = new LinkedList<AbstractEdge>();
		int fromID =origin.getId();
		int toID =destination.getId();
		ArrayList<Route> routes = null;
		//mode rail
		if(mode==0)
		{
			routes = new ArrayList<Route>();
			//1. create a path from station to station
			//2. find first and last mile path
			//3. add the path to the intermodal route
			PortNode originNearestStation = origin.getNearestStation();
			PortNode destinationNearestStation = destination.getNearestStation();		
//			System.out.println("origin, destination, origin station, destination station: "
//					+origin.getName()+","+destination.getName()+","
//					+originNearestStation.getName()+","+destinationNearestStation.getName());
			
			//station to station path
			//but if road edges are included the path may not use solely rail edges!
			path = (LinkedList<AbstractEdge>) network.getPathRail(originNearestStation, destinationNearestStation);
			
			//first mile path, this is where centroid is added to the route but never to the network
			LinkedList<AbstractEdge> firstMilePath = 
					(LinkedList<AbstractEdge>) network.getPathRoad(origin, originNearestStation);
			
			//last mile path
			LinkedList<AbstractEdge> lastMilePath = 
					(LinkedList<AbstractEdge>) network.getPathRoad(destinationNearestStation, destination);
			
			path.addAll(firstMilePath);
			path.addAll(lastMilePath);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);
			
			//adding first, and last mile path to the rail network so that it can be visualized later on
			for (AbstractEdge edge: firstMilePath)
			{
				network.getRailNetworkGraph().addEdge(edge, edge.getEndpoints());
			}
			
			for (AbstractEdge edge: lastMilePath)
			{
				network.getRailNetworkGraph().addEdge(edge, edge.getEndpoints());
			}	
			
			//computing the costs of the shortest routes
			compute_cost_matrices(routes, fromID, toID, mode);	
		}
		
		//mode road, for mode road, the centroid can be directly taken from the network
		//this means when the road network is created the centroid needs to be parsed otherwise no path will be found
		if(mode==1||mode==3||mode==4)
		{
			routes = new ArrayList<Route>();
//			System.out.println("computing cost for mode road, from,to: "+fromID+","+toID);
			path = (LinkedList<AbstractEdge>) network.getPathRoad(origin, destination);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);	
			compute_cost_matrices(routes, fromID, toID, mode);
		}
		
		//mode sea
		if(mode==2)
		{
			routes = new ArrayList<Route>();
			Set<PortNode> originPorts = origin.getPorts();
			Set<PortNode> destinationPorts = destination.getPorts();
			
			for (PortNode originPort : originPorts) {
				for (PortNode destinationPort : destinationPorts) {
					
					if(originPort==destinationPort){
						continue;
					}
					
					path = (LinkedList<AbstractEdge>) 
							network.getPathBetweenPorts(originPort, destinationPort);
				
					//this can be based on road network to the port connection
					AbstractEdge originHinterlandEdge = origin.getHinterlandConnection(originPort);
					
//					AbstractEdge originHinterlandEdge1 =network.getSeaNetworkGraph().findEdge(originHinterlandEdge.getEndpoints().getFirst(),
//							originHinterlandEdge.getEndpoints().getSecond());
					
					path.addFirst(originHinterlandEdge);
					
					AbstractEdge destinationHinterlandEdge = destination.getHinterlandConnection(destinationPort);
					
					path.addLast(destinationHinterlandEdge);
					
					Route route = new Route(origin, destination, originPort,
							destinationPort, path);
					
					//setting the network for the route, so that the route can access the hinterland connections 
					route.setNetwork(network);
					routes.add(route);
					
					compute_cost_matrices(routes, fromID, toID, mode);	
				}
			}
		}
		
		ChoiceSet choiceSet = new ChoiceSet(routes);
		return choiceSet;
	}

	private void compute_cost_matrices(ArrayList<Route> routes, int fromID, int toID, int mode) {
		double minCost,minDistance,minTravelTime, risk=0.0;
		//computing generalized costs for this specific mode
		if(routes.isEmpty())
		{
			System.out.println("routes from and to: "+fromID+","+toID+" are empty");
			minCost=Double.MAX_VALUE;
			minDistance=Double.MAX_VALUE;
			minTravelTime= Double.MAX_VALUE;
		}
		else
		{
			minCost=routes.get(0).getCosts();
			minDistance=routes.get(0).getLength();
			minTravelTime=routes.get(0).getTravelTime();
		}
		
		//setting risk level scenario
		if(mode==0)
		{
			//baseline
			risk=0.75;
			
			//basic
//			risk=0.85;
			
			//moderate
//			risk=0.95;
			
			//strong
//			risk=0.99;
		}
		else
		{
			risk=0.95;
		}
		//calculating the min cost, distance, and time
		for (Route r : routes) 
		{				
			double cost=r.getCosts();
			double time=r.getTravelTime();
			double distance=r.getLength();	
			
//			//computing the min cost
			if(cost<minCost)
			{
				minCost=cost;
			}
			
			if(distance<minDistance)
			{
				minDistance=distance;
			}
			
			if(time<minTravelTime)
			{
				minTravelTime=time;
			}
			
			//if there is no network
			if (minCost==0)
			{
					minCost= 1000000;
//				minCost=0;
			}
		}
		//printing Jakarta Surabaya costs
		if(fromID==2 && toID==109)
		{
			System.out.println("from, to, mode, distance, time, cost, "
					+this.centroidMap.get(fromID).getName()+","
					+this.centroidMap.get(toID).getName()+","+mode+","
					+minDistance+","+minTravelTime+","+minCost);
		}
		
		//write matrices of cost based on origin and destination and update the cost directly
		OD.putODCost(fromID, toID, mode, minCost);
		OD.putODTime(fromID, toID, mode, minTravelTime);
		OD.putODDistance(fromID, toID, mode, minDistance);
		OD.putODRiskSafety(fromID, toID, mode, risk);
	}
	
	/**
	 * returns the set of possible routes between the origin country and
	 * destination country.
	 * 
	 * @param origin
	 * @param destination
	 * @return a ChoiceSet with the candidate routes connecting the specified
	 *         origin and destination
	 */
	public ChoiceSet getChoice(Centroid origin, Centroid destination, int mode)
	{
//		ChoiceSet choice_of_Modes = new Choice
		if(mode==0)
		{
			return this.choiceSetRail.get(origin).get(destination);
		}
		//for mode 1 road_HDV, 3 road_MDV, 4, road_LDV, and the choiceset road just needs to be built once as long as mode 1 is built
		if(mode==1|| mode==3 || mode==4)
		{
			return this.choiceSetRoad.get(origin).get(destination);
		}
		else
		{
			System.out.println("WARNING mode is not included in the choice network!");
			return null;
		}
	}
	
	public ChoiceSet getChoice(Centroid origin, Centroid destination)
	{
//		ChoiceSet choice_of_Modes = new Choice
		if(mode==0)
		{
			return this.choiceSetRail.get(origin).get(destination);
		}
		if(mode==1)
		{
			return this.choiceSetRoad.get(origin).get(destination);
		}
		else
		{
			System.out.println("WARNING mode is not included in the choice network!");
			return null;
		}
	}

	/**
	 * reset the choices in the choice map. This means that the assigned flows
	 * of the routes are set to zero
	 */
	public void reset(){
		Collection <Centroid> centroids =  this.network.getCentroids().values();
		for (Centroid origin : centroids) {
			for (Centroid destination : centroids) {
				if(origin==destination){
					continue;
				}
				this.getChoice(origin, destination, mode).resetRouteFlow();
			}
		}
	}

	public int getMode() {
		return mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}
}
