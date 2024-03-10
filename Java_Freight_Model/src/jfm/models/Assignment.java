package jfm.models;
/* 
 * this class is created to:
 *1. build choice set for each network
 *2. perform assignment for each network
 *for each mode
 *
 */

import java.awt.PageAttributes.OriginType;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.uci.ics.jung.graph.util.Pair;
import jfm.choiceset.ChoiceSet;
import jfm.choiceset.Route;
import jfm.commodities.Commodity;
import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.AbstractServiceEdge;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MultiModalNetwork;
import jfm.network.PortNode;


public class Assignment {
	
//	private HashMap<Centroid, HashMap<Centroid, ChoiceSet>> choicesSet
//	= new HashMap<Centroid, HashMap<Centroid,ChoiceSet>>();
	private int mode;
	ChoiceSet choiceSet;
	Logit logit;
	private double mu = 0.004529; // straight from original wcm 
	private static final double epsilon = 0.0001; 
	private ArrayList<Route> routes; 
	private Collection<Centroid> centroids;
	private List<PortNode> portlist;
	private double generalizedCost[][];
	private int commodityIndex=0;
	
	private String outputDir= "output/ChoiceSetInfo";	

	//constructor for international freight model from centroid to centroid
	public Assignment(MultiModalNetwork freightNetwork,int mode) {
		this.mode=mode;
//		System.out.println("performing assignment for mode: "+mode);
		
		centroids = freightNetwork.getCentroids().values();		
		ArrayList<Commodity> commodities= freightNetwork.getCommodities();
		int commoditiesSize=commodities.size();
		
		for (Centroid origin : centroids) {
//			java.lang.System.out.println("Performing assignment for mode: "+mode+" origin: "+origin.getName());	
			for (Centroid destination : centroids) {
				if(origin.getISOAlpha3().equalsIgnoreCase(destination.getISOAlpha3())){
//					System.out.println("skipping assignment from:"+origin.getName()
//					+"("+origin.getISOAlpha3()+") to:"+destination.getName()+"("+destination.getISOAlpha3()+")");
					continue;
				}
				
				//building choice set for each origin and destination
				int fromID =origin.getId();
				int toID =destination.getId();
						
				//for each commodity, we assign the flow onto the network
				for (int i=0; i<commoditiesSize; i++)
				{					
					double flow =freightNetwork.getBaseOD().getWeightODByModes(fromID, toID, mode)[i];
					//only assign commodities that have flows
					//for each OD pair that is not zero, create a choice set and then 
					//apply logit for this route and assign weights based on commodity information
					if(flow>0)
					{
						//building choice set for a specific mode for all OD pairs for all commodities
//						java.lang.System.out.println("building choiceset "+
//						"from origin: "+origin.getName()+" to:"+destination.getName()+
//						" for commodity: "+commodities.get(i).getName()+" flow: "+flow);
						choiceSet = this.buildChoiceSet(origin, destination, freightNetwork);
						logit = new Logit(choiceSet);
						logit.direcLogit(origin,destination,flow,commodityIndex,mode);
					}
//					else
//						java.lang.System.out.println("Skipping assignment "+
//								"from origin: "+origin.getName()+" to:"+destination.getName()+
//								" for commodity: "+commodities.get(i).getName()+" flow: "+flow);
				}
				//you can reset the choice set here to free some memory
				//maybe the routes are already cleared by gc?
//				choiceSet.reset();
			}
		}
	}
	
	//constructor for international freight model without service network
	public Assignment(MultiModalNetwork freightNetwork,int mode, boolean printChoiceSet, 
			boolean calibration) throws IOException {
		this.mode=mode;
	
//		System.out.println("performing assignment for mode: "+mode);
		Collection<Centroid> centroids = freightNetwork.getCentroids().values();		
		ArrayList<Commodity> commodities= freightNetwork.getCommodities();
		int commoditiesSize=commodities.size();
		this.generalizedCost=new double[centroids.size()+1][centroids.size()+1];
		double [][] carbonIntensity = freightNetwork.getCarbonIntensity();
		BufferedWriter export = null;
		
		if(printChoiceSet)
		{
			FileOutputStream fos = new FileOutputStream(outputDir+".csv");
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			export = new BufferedWriter(osw);
			export.write("origin_city,destination_city,commodity_index,container_type,"
					+ "origin_port,destination_port,route_number,logit_share,"
					+ "flow(ton),cost,distance(km)");
			export.newLine();
		}

		for (Centroid origin : centroids) {
			//				java.lang.System.out.println("Performing assignment for mode: "+mode+" origin: "+origin.getName());	
			for (Centroid destination : centroids) {
				if(origin.getISOAlpha3().equalsIgnoreCase(destination.getISOAlpha3())){
					//						System.out.println("skipping assignment from:"+origin.getName()
					//						+"("+origin.getISOAlpha3()+") to:"+destination.getName()+"("+destination.getISOAlpha3()+")");
					continue;
				}

				//building choice set for each origin and destination
				int fromID =origin.getId();
				int toID =destination.getId();

				//creating the choice set
				choiceSet = this.buildChoiceSet(origin, destination, freightNetwork);

				//applying logit
				routes = this.choiceSet.getRoutes();

				double denominator = 0;
				double logSr;
				
				//using the first cost as a reference to search for the minimum cost
				double minCost;
				
				if(routes.isEmpty())
				{
//					System.out.println("routes from and to: "+fromID+","+toID+" are empty");
					minCost=Double.MAX_VALUE;
				}
				else
					minCost=routes.get(0).getCosts();
					
				//calculating the denominator and getting the cheapest path
				for (Route route : routes) {
					logSr = Math.log(route.getSr());
					if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
						logSr = 0;
					}
					denominator += Math.exp(-mu*(route.getCosts()- logSr ));
					double cost=route.getCosts();
					//computing the min cost
					if(cost<minCost)
					{
						minCost=cost;
					}
				}
				
				//write a matrice of cost based on origin and destination or update the cost directly
				generalizedCost[fromID][toID]=minCost;
//				System.out.println("mode, from, to, cost: "+mode+","+fromID+","+toID+","+generalizedCost[fromID][toID]);
				
				//calculating the derivative value for the port flow
				//calibration only for container flows
				if (calibration)
				{
					doCalibration(freightNetwork, mode, commodities, fromID, toID, denominator);					
				}

				double cumulAssignedFlow = 0; //merely a consistency check
				double nominator;
				//flow accross routes
				double totalFlow=0;

				//assignment
				int i=1;
				//for each route
				for (Route route : routes){
					logSr = Math.log(route.getSr());
					if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
						logSr = 0;
					}			

					nominator = Math.exp(-mu*(route.getCosts() - logSr));
					double logitShare= nominator / denominator;
					
					//there can be a loop for commodities and assignment
					//for each commodity, we assign the flow onto the network
					for (int index=0; index<commoditiesSize; index++)
					{					
						double flow =freightNetwork.getBaseOD().getWeightODByModes(fromID, toID, mode)[index];
						//only assign commodities that have flows
						//for each OD pair that is not zero, create a choice set and then 
						//apply logit for this route and assign weights based on commodity information
						
						Commodity commodity= commodities.get(index);

						//and assign the weight based on the container type
						int containerType = commodity.getContainer();
						int commodityIndex = commodity.getID();
						
						//FIXME: only performing assignment for container!!
						if(flow>0)
						{
//							building choice set for a specific mode for all OD pairs for all commodities
//							java.lang.System.out.println("building choiceset "+
//									"from origin: "+origin.getName()+" to:"+destination.getName()+
//									" for commodity: "+commodities.get(i).getName()+" flow: "+flow);

							double assignedFlow = logitShare * flow;
								
							//container 0 = container, 1= dry bulk, 2=liquid bulk, 3= general cargo
							//4=roro
							route.setCarbonIntensity(carbonIntensity);
							route.addWeightByCargo(assignedFlow, containerType, commodity.getTEU_load());
							route.addTVkm(assignedFlow, commodity.getTruckload(),commodityIndex, mode);
												
							//assignment of the aggregate flows on the route
							route.addAssignedFlow(assignedFlow);
							cumulAssignedFlow += assignedFlow;
							totalFlow+=flow;
							
							if(printChoiceSet)
							{
								String originPort = route.getOrigin().getName();
								String destinationPort = route.getDestination().getName();
								double cost =route.getCosts();
								double distance = route.getLength();
											
								export.write(origin.getName()+","+destination.getName()+","+commodityIndex+","+containerType
								+","+originPort+","+destinationPort+","+i+","+logitShare+","+flow+","+cost+","+distance);
								export.newLine();
								i++;
							}
						}
//						else
//							java.lang.System.out.println("Skipping assignment "+
//									"from origin: "+origin.getName()+" to:"+destination.getName()+
//									" for commodity: "+commodities.get(i).getName()+" flow: "+flow);
					}
				}
				double difference = cumulAssignedFlow - totalFlow;
				if(difference > epsilon){
					java.lang.System.out.print("flow: "+Double.toString(totalFlow)+ " assigned flow: "+Double.toString(cumulAssignedFlow)+"\n");
				}
				//you can reset the choice set here to free some memory
				//maybe the routes are already cleared by gc?
				//					choiceSet.reset();
			}
		}
		if(printChoiceSet)
		{
			export.close();
		}
	}

	private void doCalibration(MultiModalNetwork freightNetwork, int mode, ArrayList<Commodity> commodities, int fromID,
			int toID, double denominator) {
		//containerized commodities: 6,8,16,25
		double[] flows = freightNetwork.getBaseOD().getWeightODByModes(fromID, toID, mode);
		//compute the total container flows
		double containerFlow=flows[5]/commodities.get(5).getTEU_load()
				+flows[7]/commodities.get(7).getTEU_load()
				+flows[15]/commodities.get(15).getTEU_load()
				+flows[24]/commodities.get(24).getTEU_load();
		
		for (PortNode port: portlist)
		{
			double sumdVXdport = 0;
			//compute the derivative value of the port utility function
			for (Route route : routes) {
				if (route.getOrigin()==port||route.getDestination()==port)
				{
					sumdVXdport=sumdVXdport-(mu*Math.exp(-mu*(route.getCosts())));
				}
			}

			//computing the derivative value of port flows
			for (Route route : routes) {
				double fr = Math.exp(-mu*(route.getCosts()));
				double dfRdport = (-mu)*fr;
				if (route.getOrigin()==port)
				{
					//changes in flow = old flow value + changes in the value
					double dPrFlowdport = route.getDerivativeOriginPort() 
							+ 
							((((dfRdport*denominator)-(fr*sumdVXdport))/(denominator*denominator))*containerFlow);
					//put derivative value for origin port and destination port
					route.setDerivativeOriginPort(dPrFlowdport);
				}else
				{
					if(route.getDestination()==port)
					{
						double dPrFlowdport = route.getDerivativeDestinationPort() 
								+ 
								((((dfRdport*denominator)-(fr*sumdVXdport))/(denominator*denominator))*containerFlow);
						//put derivative value for origin port and destination port
						route.setDerivativeDestinationPort(dPrFlowdport);
					}
				}
			}
		}
	}
	
	
	
	/**
	 * helper function for making the choice set of a given origin country
	 * and destination country
	 * @param origin
	 * @param destination
	 */
	private ChoiceSet buildChoiceSet(Centroid origin, 
			Centroid destination, MultiModalNetwork network){
		
		ArrayList<Route> routes = new ArrayList<Route>();
		//the path is changed to abstract edge because we don't have service line here
		LinkedList<AbstractEdge> path = new LinkedList<AbstractEdge>();
		
		if(mode==0)
		{
			path = (LinkedList<AbstractEdge>) network.getPathAir(origin, destination);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);
		}
		
		if(mode==1)
		{
			path = (LinkedList<AbstractEdge>) network.getPathRail(origin, destination);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);
		}
		
		if(mode==2)
		{
			path = (LinkedList<AbstractEdge>) network.getPathRoad(origin, destination);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);
		}
		
		//differentiation among choice network
		if(mode==3 || mode==4)
		{
			Set<PortNode> originPorts = origin.getPorts();
			Set<PortNode> destinationPorts = destination.getPorts();
			
			//adding all the ports of the choice set
			portlist = new ArrayList<PortNode>();
			portlist.addAll(originPorts);
			portlist.addAll(destinationPorts);
			
			for (PortNode originPort : originPorts) {
				for (PortNode destinationPort : destinationPorts) {
					
					if(originPort==destinationPort){
						continue;
					}
					
					path = (LinkedList<AbstractEdge>) 
							network.getPathBetweenPorts(originPort, destinationPort);
				
					AbstractEdge originHinterlandEdge = origin.getHinterlandConnection(originPort);
					
//					AbstractEdge originHinterlandEdge1 =network.getSeaNetworkGraph().findEdge(originHinterlandEdge.getEndpoints().getFirst(),
//							originHinterlandEdge.getEndpoints().getSecond());
					
					path.addFirst(originHinterlandEdge);
					
					AbstractEdge destinationHinterlandEdge = destination.getHinterlandConnection(destinationPort);
//					AbstractEdge destinationHinterlandEdge1 =network.getSeaNetworkGraph().findEdge
//							(destinationHinterlandEdge.getEndpoints().getFirst(),
//							destinationHinterlandEdge.getEndpoints().getSecond());
					
					path.addLast(destinationHinterlandEdge);
					
					Route route = new Route(origin, destination, originPort,
							destinationPort, path);
					
					//setting the network for the route, so that the route can access the hinterland connections 
					route.setNetwork(network);
					routes.add(route);
				}
			}
		}

		//TODO: route has to be modified to be include port of origin and destination
		//currently this route only contains the shortest path for maritime network
//		Route route = new Route(origin, destination, null,null, path);
//		routes.add(route);
		ChoiceSet choiceSet = new ChoiceSet(routes);
		return choiceSet;
	}

	/**
	 * reset the choices in the choice map. This means that the assigned flows
	 * of the routes are set to zero
	 */
	public void reset(){
		this.choiceSet.reset();
	}

	public int getMode() {
		return mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}

	public double[][] getGeneralizedCost() {
		return generalizedCost;
	}

	public void setGeneralizedCost(double[][] generalizedCost) {
		this.generalizedCost = generalizedCost;
	}

}
