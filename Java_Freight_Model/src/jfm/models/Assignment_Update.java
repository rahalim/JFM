package jfm.models;
/* 
 * this class is created to:
 *1. build choice set for each network
 *2. perform assignment for each network
 *for each mode
 *
 */

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import jfm.choiceset.ChoiceNetwork;
import jfm.choiceset.ChoiceSet;
import jfm.choiceset.Route;
import jfm.commodities.Commodity;
import jfm.network.AbstractEdge;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.network.PortNode;
import jfm.scenario.ODdata;


public class Assignment_Update {
	
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
	
	//custom attributes for commodity for the first version
	private int commodityIndex =0;
	private int commoditiesSize=1;
	private double TEU_load =14;//14Ton/TEU
	private int containerType=1;
	private double Truck_load = 16;
	
	//origin, destination, transition, volume and value, tkm
	private HashMap<String,HashMap<String,HashMap<String,double[]>>> ODflows;
	private String outputDir= "output/ChoiceSetInfo";	

	//constructor for international freight model from centroid to centroid
	//in this version a choice set is created for each commodity which is not very efficient
	public Assignment_Update (MultiModalNetworkCostNoSQL freightNetwork,int mode) {
		this.mode=mode;
//		System.out.println("performing assignment for mode: "+mode);
		
		centroids = freightNetwork.getCentroids().values();		
		int commoditiesSize=freightNetwork.getCommoditySize();
		
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
	
	
	//constructor for international freight model with multimodal network cost
	public Assignment_Update (MultiModalNetworkCostNoSQL freightNetwork,ChoiceNetwork choiceNetwork, int mode, ODdata OD, 
			boolean printChoiceSet, boolean calibration) throws IOException {
		//setting the mode so that the network that is used going to be only for this mode
		this.mode=mode;
		
		double totalCost=0;
		double totalTEU=0;
		double totalRailDistance=0;
		double avgDistance=0;
		int routeNo=0;
	
		//creating new OD flow object to reset the flows
		this.ODflows=new HashMap <String,HashMap<String,HashMap<String,double[]>>>();
		
//		System.out.println("performing multimodal network assignment for mode: "+mode);
		
		Collection<Centroid> centroids = freightNetwork.getCentroids().values();		
		ArrayList<Commodity> commodities= freightNetwork.getCommodities();

		//preparing matrix for generalized cost for this particular mode
		this.generalizedCost=new double[centroids.size()+1][centroids.size()+1];
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
			for (Centroid destination : centroids) 
			{							
				//skipping origin=1 and destination=1
				if(origin.getName().equalsIgnoreCase(destination.getName()) || origin.getId()==1 || destination.getId()==1)
				{
//					System.out.println("skipping assignment from:"+origin.getName()
//					+" to:"+destination.getName());
					continue;
				}

				//getting the previously created choiceset
				choiceSet = choiceNetwork.getChoice(origin, destination, mode);

				//applying logit
				routes = this.choiceSet.getRoutes();
				
//				System.out.println("mode, from, to, cost: "+mode+","+fromID+","+toID+","+generalizedCost[fromID][toID]);
				
				int fromID =origin.getId();
				int toID =destination.getId();
				double cumulAssignedFlow = 0; //merely a consistency check
				double nominator;
				//flow accross routes
				double totalFlow=0;
				double denominator = 0;
				double logSr;			
				//calculating the denominator and getting the cheapest path
				for (Route route : routes) {
					logSr = Math.log(route.getSr());
					if((logSr==Double.POSITIVE_INFINITY)||(logSr==Double.NEGATIVE_INFINITY)){
						logSr = 0;
					}
					denominator += Math.exp(-mu*(route.getCosts()- logSr ));
				}
				
				//calculating the derivative value for the port flow
				//calibration only for container flows
				if (calibration)
				{
					doCalibration(freightNetwork, mode, commodities, fromID, toID, denominator);					
				}
				
				//assignment
				int i=1;
				//for each route
				for (Route route : routes)
				{
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
//						Commodity commodity= commodities.get(index);

						if(flow>0)
						{
//							building choice set for a specific mode for all OD pairs for all commodities
//							java.lang.System.out.println("building choiceset "+
//									"from origin: "+origin.getName()+" to:"+destination.getName()+
//									" for commodity: 0"+" flow: "+flow);

							double assignedFlow = logitShare * flow;
							//assign the weight based on the container type
							//container 0 = container, 1= dry bulk, 2=liquid bulk, 3= general cargo 4=roro
							route.addWeightByCargo(assignedFlow, containerType, TEU_load);
							route.addTVkm(assignedFlow, Truck_load,commodityIndex, mode);

							//computing transport cost per route
							totalCost+=route.getCosts()*assignedFlow;
							totalTEU+=assignedFlow/13;
							
							//assignment of the aggregate flows on the route
							route.addAssignedFlow(assignedFlow);
							cumulAssignedFlow += assignedFlow;
							totalFlow+=flow;
							
							totalRailDistance=totalRailDistance+route.getRailLength();
							routeNo++;

							if(printChoiceSet)
							{
								printChoiceSet(export, origin, destination, i, route, logitShare, flow, containerType,
										commodityIndex);
								i++;
							}	
						}
//						else
//							java.lang.System.out.println("Skipping assignment "+
//									"from origin: "+origin.getName()+" to:"+destination.getName()+
//									" for commodity: "+commodities.get(i).getName()+" flow: "+flow);
					}//end of the commodity loop
			
					//routines to compute flows at transit countries
//					LinkedList<AbstractEdge> path=(LinkedList<AbstractEdge>) route.getPath();	
//					double routeFlow = route.getAssignedFlow();
//					
//					//1.creating a list of transit countries for each route
//					HashSet<String> transitCountries= new HashSet<String>();
//					
//					//2.only assign to routes that have flow
//					if(routeFlow>0)
//					{
//						computeTransitFlows(origin, destination, path, routeFlow, transitCountries);						
//					}//end of if flow>0 condition
						
				}//for loop for routes	
				
				double difference = cumulAssignedFlow - totalFlow;
				if(difference > epsilon){
					java.lang.System.out.print("flow: "+Double.toString(totalFlow)+ " assigned flow: "+Double.toString(cumulAssignedFlow)+"\n");
				}
				//you can reset the choice set here to free some memory
				//maybe the routes are already cleared by gc?
				//					choiceSet.reset();
			}
		}
		avgDistance=totalRailDistance/routeNo;
		System.out.println("total transport costs of mode "+mode+" is: "+totalCost);
		System.out.println("total TEU of mode "+mode+" for government is: "+totalTEU);
		System.out.println("average transport distance of rail mode "+mode+"  is: "+avgDistance);
		OD.status="updated";
		if(printChoiceSet)
		{
			export.close();
		}
	}

	private void printChoiceSet(BufferedWriter export, Centroid origin, Centroid destination, int i, Route route,
			double logitShare, double flow, int containerType, int commodityIndex) throws IOException {
		String originPort = route.getOrigin().getName();
		String destinationPort = route.getDestination().getName();
		double cost =route.getCosts();
		double distance = route.getLength();

		export.write(origin.getName()+","+destination.getName()+","+commodityIndex+","+containerType
				+","+originPort+","+destinationPort+","+i+","+logitShare+","+flow+","+cost+","+distance);
		export.newLine();
	}

	private void computeTransitFlows(Centroid origin, Centroid destination, LinkedList<AbstractEdge> path,
			double routeFlow, HashSet<String> transitCountries) {
		//						java.lang.System.out.println("Performing assignment for origin, destination, flow: "+origin.getName()+","
		//								+destination.getName()+", "+routeFlow);

		String ISO_O=origin.getISOAlpha3();
		String ISO_D=destination.getISOAlpha3();
		double transit_distance=0;

		//						System.out.println("INPUT: origin, destination, flow:"+ISO_O+","+ISO_D+","+routeFlow);
		for (AbstractEdge edge : path) 
		{
			//only for land based mode
			if(edge.getMode().equalsIgnoreCase("Road" ))
			{
				if(edge.getISO()==null)
				{
					System.out.println(edge.getRindex());
					edge.setISO("Unknown");

				}
				String ISO_T=edge.getISO();
				//implement rule to check and filter the origin and destination and whether those data already exist or not
				//if edges are in the transit countries, record the flows else do nothing
				// we have to record the domestic flow here
				//create new entry in the OD flow
				if (ISO_T.equalsIgnoreCase(ISO_O))
				{//TODO: record the distance of the domestic link
					//									System.out.println("ISO_O,ISO_D,domestic link origin: "+ISO_O+","+ISO_D+","+ISO_T);
					transitCountries.add("domestic_origin");
				}

				else if (ISO_T.equalsIgnoreCase(ISO_D))
				{
					//									System.out.println("ISO_O,ISO_D,domestic link destination: "+ISO_O+","+ISO_D+","+ISO_T);
					transitCountries.add("domestic_destination");
					//print result and continue
					//									System.out.println("OD pairs with new destination country:"+ISO_O+","+ISO_D+","+routeFlow);
				}
				else
				{
					//									System.out.println("transitcountry: "+edge.getISO());
					transitCountries.add(ISO_T);
					//if origin already exists
					if(this.ODflows.get(ISO_O)!=null)
					{
						//if origin and destination already exist
						if(this.ODflows.get(ISO_O).get(ISO_D)!=null)
						{
							//if origin and destination and transit already exist
							if(ODflows.get(ISO_O).get(ISO_D).get(ISO_T)!=null)
							{
								//we don't need to store the transit country since it already existed
								//do nothing
								//update the previously stored distance for the country
								double length=this.ODflows.get(ISO_O).get(ISO_D).get(ISO_T)[1];
								double transitDistance = length+edge.getLength();
								this.ODflows.get(ISO_O).get(ISO_D).get(ISO_T)[1]=transitDistance;
							}
							//origin and destination exist but transit has not existed (new transit)
							else 
							{
								this.ODflows.get(ISO_O).get(ISO_D).put(ISO_T, new double[2]);
								transit_distance=edge.getLength();
								double[] data = new double[2];
								data[1] = transit_distance;

								this.ODflows.get(ISO_O).get(ISO_D).put(ISO_T, data);

								//												System.out.println("INPUT3: origin, destination, transit, volume: "
								//														+origin.getISOAlpha3()+","+destination.getISOAlpha3()+","+ISO_T);
							}
						}
						//origin exist but destination doesn't exist yet (because of a new country)
						else
						{
							this.ODflows.get(ISO_O).put(ISO_D, new HashMap<String, double[]>());
							this.ODflows.get(ISO_O).get(ISO_D).put(ISO_T, new double[2]);

							transit_distance=edge.getLength();
							double[] data = new double[2];
							data[1] = transit_distance;

							this.ODflows.get(ISO_O).get(ISO_D).put(ISO_T, data);

							//											System.out.println("INPUT2: origin, destination, transit, volume: "
							//													+origin.getISOAlpha3()+","+destination.getISOAlpha3()+","+ISO_T);
						}
					}
					//origin doesn't exist yet, fill up the map
					else
					{
						this.ODflows.put(ISO_O, new HashMap<String, HashMap<String,double[]>>());	
						this.ODflows.get(ISO_O).put(ISO_D, new HashMap<String, double[]>());

						transit_distance=edge.getLength();
						double[] data = new double[2];
						data[1] = transit_distance;

						this.ODflows.get(ISO_O).get(ISO_D).put(ISO_T, data);
						//										System.out.println("INPUT1: origin, destination, transit, volume: "
						//												+origin.getISOAlpha3()+","+destination.getISOAlpha3()+","+ISO_T);
					}
				}
			}
		}//for loop, for edges

		//assigning the route volume to all the countries along the route
		double prev_weight=0;
		double prev_distance=0;
		double weight=0;
		double tkm=0;

		//neighbouring country that is not recorded in the first place, 
		//since the edges are always part of either origin or destination
		if(this.ODflows.get(ISO_O)==null)
		{
			//print result and continue
			//							System.out.println("first entry of origin, Output:"+ISO_O+","+ISO_D+","+routeFlow);
			double[] origin_data= new double[2];
			double[] destination_data= new double[2];
			double[] neighbouring_data=new double[2];

			this.ODflows.put(ISO_O, new HashMap<String, HashMap<String,double[]>>());
			this.ODflows.get(ISO_O).put(ISO_D, new HashMap<String, double[]>());

			//							if(mode==0|| mode==1|| mode==3)
			//							{
			this.ODflows.get(ISO_O).get(ISO_D).put("domestic_origin",origin_data);
			this.ODflows.get(ISO_O).get(ISO_D).put("domestic_destination",destination_data);
			//transitCountries.add("domestic link");	
			//								System.out.println("no origin and destination yet domestic link_origin and destination");
			//							}

		}
		else
			//if there is origin but no destination country
			if(this.ODflows.get(ISO_O).get(ISO_D)==null)
			{
				//print result and continue
				//							System.out.println("OD pairs with new destination country:"+ISO_O+","+ISO_D+","+routeFlow);
				double[] data= new double[2];
				data[0]=0;
				double[] neighbouring_data= new double[2];
				neighbouring_data[0]=0;

				this.ODflows.get(ISO_O).put(ISO_D, new HashMap<String, double[]>());

				this.ODflows.get(ISO_O).get(ISO_D).put("domestic_destination",data);
				//							System.out.println("origin available, domestic link_destination");

			}
		//if there's origin and destination but no domestic origin  yet
		//third possibility, checking od flow database and add domestic link if it doesn't exist yet
		if(this.ODflows.get(ISO_O).get(ISO_D).get("domestic_origin")==null)
		{
			double[] origin_data= new double[2];
			this.ODflows.get(ISO_O).get(ISO_D).put("domestic_origin", origin_data);									
		}

		if(this.ODflows.get(ISO_O).get(ISO_D).get("domestic_destination")==null)
		{
			double[] destination_data= new double[2];
			this.ODflows.get(ISO_O).get(ISO_D).put("domestic_destination", destination_data);									
		}

		//assigning/updating the weight to the countries along the routes
		//but this does not include the domestic link
		//						for(String T: this.ODflows.get(ISO_O).get(ISO_D).keySet())
		for (String T : transitCountries) 
		{
			//update the weight and the tkm except for the domestic link because we don't know
			//							if(T.equalsIgnoreCase("domestic link")|| T.equalsIgnoreCase("neighbouring"))
			//							{
			//								System.out.println("O,D, neighbouring or domestic link:"+ISO_O+","+ISO_D+","+T);
			//								continue;
			//							}							
			//							else
			//							{
			//this will update all the transit countries registered between OD pairs and  not based on the routes
			//assignment based on the routes need to be implemented
			//first create an additional list of transit countries per route and then
			//loop over these countries to add them into the compiled list of all transit countries

			//								prev_weight=this.ODflows.get(ISO_O).get(ISO_D).get(T)[0];
			//								prev_distance=this.ODflows.get(ISO_O).get(ISO_D).get(T)[1];
			//								weight = prev_weight+routeFlow;
			//								System.out.println("previous weight, weight: "+prev_weight+","+weight);

			//assign the weight and tkm on the route
			this.ODflows.get(ISO_O).get(ISO_D).get(T)[0]+=routeFlow;
			//								System.out.println("Output:"+ISO_O+","+ISO_D+","+T+","+this.ODflows.get(ISO_O).get(ISO_D).get(T)[0]);
			//							}
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
	
	
	 /* helper function for making the choice set of a given origin country
	 * and destination country
	 * @param origin
	 * @param destination
	 */
	private ChoiceSet buildChoiceSet(Centroid origin, 
			Centroid destination, MultiModalNetworkCostNoSQL network){
		
		ArrayList<Route> routes = new ArrayList<Route>();
		//the path is changed to abstract edge because we don't have service line here
		LinkedList<AbstractEdge> path = new LinkedList<AbstractEdge>();
		//mode rail
		if(mode==0)
		{
			//1. create a path from station to station
			//2. find first and last mile path
			//3. add the path to the intermodal route
			PortNode originNearestStation = origin.getNearestStation();
			PortNode destinationNearestStation = destination.getNearestStation();		
//			System.out.println("origin, destination, origin station, destination station: "
//					+origin.getName()+","+destination.getName()+","
//					+originNearestStation.getName()+","+destinationNearestStation.getName());
			
			//station to station path
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
		}
		
		//mode road, for mode road, the centroid can be directly taken from the network
		//this means when the road network is created the centroid needs to be parsed otherwise no path will be found
		if(mode==1)
		{
			path = (LinkedList<AbstractEdge>) network.getPathRoad(origin, destination);
			Route route = new Route(origin, destination, null,null, path);
			routes.add(route);
		}
		
		//mode sea
		if(mode==2)
		{
			Set<PortNode> originPorts = origin.getPorts();
			Set<PortNode> destinationPorts = destination.getPorts();
			
//			//adding all the ports of the choice set
//			portlist = new ArrayList<PortNode>();
//			portlist.addAll(originPorts);
//			portlist.addAll(destinationPorts);
			
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
				}
			}
		}

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

	public HashMap<String, HashMap<String, HashMap<String, double[]>>> getODflows() {
		return ODflows;
	}
}
