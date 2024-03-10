package jfm.models;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;


import edu.uci.ics.jung.graph.util.Pair;
import jfm.choiceset.ChoiceNetwork;
import jfm.choiceset.ChoiceSet;
import jfm.choiceset.Route;
import jfm.commodities.Commodity;
import jfm.network.AbstractEdge;
import jfm.network.Centroid;
import jfm.network.MultiModalNetwork;
import jfm.network.PortNode;
import jfm.scenario.ODdata;
import jfm.scenario.ScenarioInterface;

public class Logit {
	private static final double epsilon = 0.0001; 
	private ChoiceSet choiceSet;

	private ChoiceNetwork choiceNetwork;
	private ODdata odData;
	private Double flow;

	private double mu = 0.004529; // straight from original wcm 
	private double unitTransportCost = 0.025; //$/km.TEU, straight from the original wcm
	private ArrayList<Route> routes; 
	
	private static double totalTransportCost=0;
	private static double totalHandlingCost=0;
	private static double totalTravelCost=0;
	private static double totalTranshipmentCost=0;
	private static double totalTurnAroundCost=0;
	private static double totalContainerDwellTimeCost=0;
	
	//additional variables for the commodity
	//TODO: since there is only one commodity, most of the attributes are hard coded here, otherwise they need an independent data structure
	private double TEU_load =14;//14Ton/TEU
	private int containerType=1;
	private double Truck_load = 16;
		
	//new constructor using ChoiceNetwork
	public Logit(ChoiceNetwork choiceNetwork, ODdata odData){
		this.choiceNetwork = choiceNetwork;
		this.odData=odData;
		
		//resetting all the cost when new choice network is created
		totalTransportCost=0;
		totalHandlingCost=0;
		totalTravelCost=0;
	}
	
	
	//third constructor used for logit function created for each choiceSet
	public Logit(ChoiceSet choiceSet)
	{
		this.choiceSet=choiceSet;
	}
	
	/**
	 * 
	 * @param scenario
	 * @throws IOException 
	 */
	public void applyLogit(ScenarioInterface scenario) throws IOException{
		Set<Centroid> origins = scenario.getOrigins();
		for (Centroid origin : origins) {
			Set<Centroid> destinations = scenario.getDestinations(origin);
			for (Centroid destination : destinations) {
				double flow = scenario.getFlow(origin, destination);
				this.logit(origin, destination, flow);
			}
		}
	}
	
	
	/**
	 * 
	 * @param origin
	 * @param destination
	 * @param flow
	 */
	private void logit(Centroid origin, Centroid destination, Double flow){
		
		ChoiceSet choiceSet = this.choiceNetwork.getChoice(origin, destination);
		
		ArrayList<Route> routes = choiceSet.getRoutes();
		
		//resetting the cost in the beginning
		totalTravelCost =0;
		totalHandlingCost=0;
		totalTranshipmentCost=0;
		totalTurnAroundCost=0;
		totalContainerDwellTimeCost=0;

		double denominator = 0;
		double logSr;
		for (Route route : routes) {
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}
			denominator += Math.exp(-mu*(route.getCosts()- logSr ));
		}
		
		double cumulAssignedFlow = 0; //merely a consistency check
		double nominator;
		double transhipmentCost=100;
		double containerDwellTime= 5; // in days, for the container
		double turnAroundTime = 2; // in days, for the ship
		double VoT=50; //with assumption of 50$/TEU.days
		double routeTranshipmentCost=0;
		double routeTurnAroundCost =0;
		
		for (Route route : routes){
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}			
			
			nominator = Math.exp(-mu*(route.getCosts() - logSr));
			double assignedFlow = nominator / denominator * flow;
			
			route.setLogitShare(nominator/denominator);
			route.addAssignedFlow(assignedFlow);
			cumulAssignedFlow += assignedFlow;
			
			if(route.getOrigin()==null||route.getDestination()==null)
			{
//				System.out.println("ignoring port to port flows for route from: "
//						+route.getOriginCountry().getName()+" to: "+route.getDestinationCountry().getName());
				continue;
			}

			//add cost for transhipment between ships and turn around time, 
			//for each of the legs travelled there's a turn around time
			LinkedList<AbstractEdge> shippingEdges=(LinkedList<AbstractEdge>) route.getPath();
		
			//for routes with multiple port stops
			routeTranshipmentCost = (shippingEdges.size()-2) *transhipmentCost*assignedFlow;
			routeTurnAroundCost = (shippingEdges.size()-2) * (turnAroundTime*VoT*assignedFlow);
			
			
			//for route without transhipment (only 3 edges)
			if(shippingEdges.size()<=3)
			{
				routeTranshipmentCost = 0;
				routeTurnAroundCost =  turnAroundTime*VoT*assignedFlow;
			}
			
			totalTranshipmentCost = totalTranshipmentCost + routeTranshipmentCost;
			totalTurnAroundCost = totalTurnAroundCost + routeTurnAroundCost;
			
//			System.out.println("OD:"+route.getOriginCountry().getName()+"-"+route.getDestinationCountry().getName()+
//					" maritime links: "+shippingEdges.size()+
//					" route trans cost: "+routeTranshipmentCost+ 
//					" route turn around cost: "+routeTurnAroundCost+
//					" totalTranshipmentCost: "+totalTranshipmentCost+
//					" totalTurnAroundCost: "+totalTurnAroundCost);
			
			//add a routine that calculates the cost over the routes
			//assignedflow*distance*unit cost/TEU.km
//			totalTravelCost = totalTravelCost + assignedFlow*route.getLength()*unitTransportCost;
			
			//get the cost from the route which includes handling cost at the port of origin and destination
//			totalHandlingCost = totalHandlingCost + assignedFlow*route.getOrigin().getHandlingCosts()+
//					assignedFlow*route.getDestination().getHandlingCosts();
			
			totalContainerDwellTimeCost = totalContainerDwellTimeCost + (assignedFlow*containerDwellTime*VoT);
			
			//this travel cost already includes handling cost
			totalTravelCost = totalTravelCost + route.getCosts()*assignedFlow;	
		}
		//total transport cost for all ODs, accumulated per OD
		totalTransportCost = totalTransportCost + 
				(totalTravelCost  + totalTranshipmentCost + totalContainerDwellTimeCost + totalTurnAroundCost);
		
		double difference = cumulAssignedFlow - flow;
		
		if(difference > epsilon){
			java.lang.System.out.print("flow: "+Double.toString(flow)+ " assigned flow: "+Double.toString(cumulAssignedFlow)+"\n");
		}
	}
	
		
	//original logit function for a choice set with a single route
	//the logit can be used with multiple choice sets
	@SuppressWarnings("unused")
	public void applyLogit (Centroid origin, Centroid destination, Double flow)
	{
		ChoiceSet choiceSet = this.choiceNetwork.getChoice(origin, destination);
		
		ArrayList<Route> routes = this.choiceSet.getRoutes();
		
//		System.out.println("origin, destination, flow: "
//		+origin.getName()+", "+destination.getName()+","+flow);

		double denominator = 0;
		double logSr;
		for (Route route : routes) {
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}
			denominator += Math.exp(-mu*(route.getCosts()- logSr ));
		}
		
		double cumulAssignedFlow = 0; //merely a consistency check
		double nominator;

		for (Route route : routes){
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}			
			
			nominator = Math.exp(-mu*(route.getCosts() - logSr));
			
			double assignedFlow = nominator / denominator * flow;
			
			//assigned flow is an aggregate weight
			route.addAssignedFlow(assignedFlow);
			cumulAssignedFlow += assignedFlow;
			
//			if(route.getOrigin()==null||route.getDestination()==null)
//			{
////				System.out.println("ignoring port to port flows for route from: "
////						+route.getOriginCountry().getName()+" to: "+route.getDestinationCountry().getName());
//				continue;
//			}
		}	
		double difference = cumulAssignedFlow - flow;
		if(difference > epsilon){
			java.lang.System.out.print("flow: "+Double.toString(flow)+ " assigned flow: "+Double.toString(cumulAssignedFlow)+"\n");
		}
	}
	

	//this is logit function without any choice network
	public void direcLogit (Centroid origin, Centroid destination, Double flow, int commodityIndex, int mode)
	{	
		routes = this.choiceSet.getRoutes();
		
//		System.out.println("origin, destination, flow: "
//		+origin.getName()+", "+destination.getName()+","+flow);

		double denominator = 0;
		double logSr;
		for (Route route : routes) {
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}
			denominator += Math.exp(-mu*(route.getCosts()- logSr ));
		}
		
		double cumulAssignedFlow = 0; //merely a consistency check
		double nominator;

		int i=1;
		for (Route route : routes){
			logSr = Math.log(route.getSr());
			if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
				logSr = 0;
			}			
			
			nominator = Math.exp(-mu*(route.getCosts() - logSr));
			double assignedFlow = nominator / denominator * flow;
			
//			System.out.println("origin, destination, commodity, container, cost:"
//					+route.getOriginCentroid().getName()+","+route.getDestinationCentroid().getName()
//					+","+commodityIndex+","+containerType+","+route.getCosts()
//					+" route "+i+" logit share: "+nominator/denominator);
//					i++;
			
			//assign the weight based on the container type and TEU load
			route.addWeightByCargo(assignedFlow, containerType,TEU_load);
			route.addTVkm(assignedFlow,Truck_load,commodityIndex, mode);
			
			route.addAssignedFlow(assignedFlow);
			cumulAssignedFlow += assignedFlow;
		}	
		double difference = cumulAssignedFlow - flow;
		if(difference > epsilon){
			java.lang.System.out.print("flow: "+Double.toString(flow)+ " assigned flow: "+Double.toString(cumulAssignedFlow)+"\n");
		}
	}
	
	//printing the choicesets in the choicenetwork
	private void printChoiceSet (Centroid origin, Centroid destination, int index) throws IOException { 
		ChoiceSet choiceSet = this.choiceNetwork.getChoice(origin, destination);
		ArrayList<Route> routes = choiceSet.getRoutes();
		
		FileOutputStream fos = new FileOutputStream("output data/MND output/choiceSetInfo_"+index+".csv");
		OutputStreamWriter osw = new OutputStreamWriter(fos);
		BufferedWriter export = new BufferedWriter(osw);
		
		export.write("origin city, destination city, origin port, destination port, logit share, flow (MTEU), cost ($), distance (km)");
		export.newLine();
		String originPort="null";
		String destinationPort="null";
		for (Route route : routes) 
		{		
			//if it is an overland connection
			if(route.getOrigin()==null)
			{
				System.out.println("overland route detected");
				double logitShare =route.getLogitShare();
				double flow = route.getAssignedFlow();
				double cost = route.getCosts();
				double distance = route.getLength();
				export.write(origin.getName()+","+destination.getName()+","+originPort+","+destinationPort+","+logitShare+","+flow
						+","+cost+","+distance);
				export.newLine();
			}
			else//if this is not an overland connection
			{
				originPort = ((PortNode)route.getOrigin()).getName();
				destinationPort= ((PortNode)route.getDestination()).getName();

				double logitShare =route.getLogitShare();
				double flow = route.getAssignedFlow();
				double cost = route.getCosts();
				double distance = route.getLength();
				export.write(origin.getName()+","+destination.getName()+","+originPort+","+destinationPort+","+logitShare+","+flow
						+","+cost+","+distance);
				export.newLine();
			}
		}
		export.close();
	}
	
	
	
	public void resetLogitRoutes(){
		for (Route route : this.routes) {	
			route.getPath().clear();
		}
		this.routes.clear();
	}
	
	public void resetLogitChoiceNetwork()
	{
		this.choiceNetwork.reset();
	}
	
	public double getTotalTransportCost()
	{
		return this.totalTransportCost;
	}
}
