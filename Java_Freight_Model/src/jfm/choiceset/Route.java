package jfm.choiceset;

import java.util.List;

import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MaritimeEdge;
import jfm.network.MultiModalNetwork;
import jfm.network.PortNode;
import jfm.network.RoadEdge;


public class Route {
	private List<AbstractEdge> path;
	private List<AbstractEdge> accessPath;
	private List<AbstractEdge> egressPath;
	private double costs = 0;
	private double length = 0;
	private double railLength=0;
	private double travelTime=0;
	private double sr; //path size overlap
	private double assignedFlow = 0;
	private double logitShare = 0;
	private double[][] carbonIntensity = new double[25][9];
	
	/* TODO the fields below are technically not needed. All the relevant
	 * information is already contained in the path itself. 
	 * moreover, route objects only exist as part of a choice set which
	 * in turn only exists in a hashmap of hashmaps, so both origin
	 * and destination country are known in this way
	 */ 
	private PortNode originPort;
	private PortNode destinationPort;
	private Centroid origin;
	private Centroid destination;
	private AbstractNode originCentroid;
	private AbstractNode destinationCentroid;
	private MultiModalNetwork network;
	private double derivativeOriginPort;
	private double derivativeDestinationPort;
	/**
	 * 
	 * @param path
	 */
	public Route(Centroid originCountry, Centroid destinationCountry,
			PortNode originPort, PortNode destinationPort, List<AbstractEdge> path){
		this.path = path;
		this.originPort = originPort;
		this.destinationPort = destinationPort;
		this.origin = originCountry;
		this.destination = destinationCountry;
		this.derivativeOriginPort=0;
		this.derivativeDestinationPort=0;
		
		/* costs is the sum of costs of traversing each edge
		 * in this implementation, costs have to be calculated 
		 * based on the shortest path distance given by a table 
		 * which was parsed earlier
		 */
//		System.out.println("from, to: "+origin.getName()+","+destination.getName());
		
		double totalLength=0;
		double edge_length=0;
		double edge_cost=0;
		double edge_time=0;
		for (AbstractEdge abstractEdge : path) 
		{	
			//computing total length only for railway
			if(abstractEdge.getMode().equalsIgnoreCase("Railway"))
			{
				this.railLength=railLength+abstractEdge.getLength();
			}
			
			edge_length = abstractEdge.getLength();
			edge_cost = abstractEdge.getCosts();
			edge_time = abstractEdge.getTravelTime();
			totalLength= totalLength+ abstractEdge.getLength();

			this.length=totalLength;
			//TODO:less elegant solution, better make trip time to be generic at abstract edge level	
//			this.travelTime+=((RoadEdge)abstractEdge).getTripTime();	
			this.costs +=abstractEdge.getCosts();
			
			this.travelTime+=abstractEdge.getTravelTime();		
//			System.out.println("fromId, toID,length, costs, travel time: "
//					+abstractEdge.getEndpoints().getFirst().getId()+","
//					+abstractEdge.getEndpoints().getSecond().getId()+","+edge_length+","+edge_cost+","+edge_time);
		}
//		System.out.println("ROUTE: from, to, length, cost, time: "+origin.getName()+","+destination.getName()+","+this.length+","+this.costs+","+this.travelTime);
	}
	
	/**
	 * set path size overlap variable
	 * 
	 * @param sr 
	 * @return
	 */
	protected void setSr(double sr){
		this.sr = sr;
	}
	
	/**
	 * get the path size overlap variable
	 * @return sr
	 */
	public double getSr(){
		return this.sr;
	}
	
	/**
	 * 
	 * @return path from origin country to destination country
	 */
	public List<AbstractEdge> getPath(){
		return this.path;
	}

	/**
	 * 
	 * @return costs
	 */
	public double getCosts() {
		return this.costs;
	}
	
	public double getLength(){
		return this.length;
	}
	
	public double getTravelTime(){
		return this.travelTime;
	}

	/**
	 * 
	 * @return port of origin
	 */
	public PortNode getOrigin() {
		return this.originPort;
	}
	
	/**
	 * 
	 * @return port of destination
	 */
	public PortNode getDestination() {
		return this.destinationPort;
	}
	
	/**
	 * 
	 * @return country of origin of route
	 */
	public Centroid getOriginCentroid() {
		return this.origin;
	}

	/**
	 * 
	 * @return destination county of route
	 */
	public Centroid getDestinationCentroid() {
		return this.destination;
	}
	
	public double getAssignedFlow(){
		return this.assignedFlow;
	}
	
	public void resetAssignedFlow(){
		this.assignedFlow = 0;
	}
	
	//designed as a method since we don't always want to measure carbon intensity
	public void setCarbonIntensity(double[][] carbonIntensity){
		this.carbonIntensity = carbonIntensity;
	}
	
	/**
	 * add flow to route. The assignedFlow is added to the already assigned 
	 * flow.
	 * 
	 * @param assignedFlow
	 */
	public synchronized void addAssignedFlow(double assignedFlow)
	{
		this.assignedFlow += assignedFlow;

		//adding assigned flow to each of the edges on the path
		for (AbstractEdge edge : this.path) 
		{
			//System.out.print(edge.getMode()+"-");
			//assignment on hinterland edge
			if(edge instanceof HinterlandEdge)
			{
				int id = ((HinterlandEdge) edge).getId();
				//				System.out.println("hinterland flow assignment: "+id);
				this.network.getPortHinterlandLinks().get(id).addAssignedFlow(assignedFlow);
				double roadTKM = ((HinterlandEdge) edge).getRoadKM()*assignedFlow;
				double railTKM =((HinterlandEdge) edge).getRailKM()*assignedFlow; 
				((HinterlandEdge) edge).setRoadTKM(roadTKM);
				((HinterlandEdge) edge).setRailTKM(railTKM);
			}
			else
			{
				//assignment on normal edge
				edge.addAssignedFlow(assignedFlow);
			}
		}
	}
	
	public void addWeightByCargo(double flow, int containerType, double TEU_load){		
		//adding assigned flow based on containertype to each of the edges on the path
		int i = -1;
		for (AbstractEdge edge : this.path) {
			edge.assignWeightByCategories(flow, containerType);
			i++;
			//assignment for port throughput
			double TEU= flow/TEU_load;

			//if container type =0; meaning this is container
			if(containerType==0)
			{
				if(edge.getClass() == MaritimeEdge.class){
					AbstractEdge nextEdge = this.path.get(i+1);
					if(nextEdge.getClass() == HinterlandEdge.class){

						//System.out.println("port throughput assignment, port, volume: "+((HinterlandEdge)nextEdge).getPort().getLabel());
						//throughput from sea to hinterland
						PortNode portIn =((HinterlandEdge)nextEdge).getPort();
						portIn.addThroughput(TEU);
						
						double sumDerivative = portIn.getSumDerivativeFlow()+this.derivativeDestinationPort;
						portIn.setSumDerivativeFlow(sumDerivative);
					} 

				} else if ((edge.getClass() == HinterlandEdge.class) && (i==0) 
						&& (this.path.size()>1)){
					if (this.path.get(i+1).getClass()==MaritimeEdge.class){
						//throughput from land to sea
						//					System.out.println("port throughput assignment, port, volume: "+((HinterlandEdge)edge).getPort().getLabel());
						PortNode portOut =((HinterlandEdge)edge).getPort();
						portOut.addThroughput(TEU);
						
						double sumDerivative2 = portOut.getSumDerivativeFlow()+this.derivativeOriginPort;
						portOut.setSumDerivativeFlow(sumDerivative2);
					}
				}
			}
		}	
	}

	public void addTVkm (double flow, double truckLoad, int commodityIndex, int mode)
	{
		double CO2 = flow*carbonIntensity[commodityIndex][2]*this.length;
		//assignment of tkm and vkm on the edges of the path
		for (AbstractEdge edge : this.path) {
			//distance is in km as calculated using haversine function
			double distance = edge.getLength();
			double tkm = flow*distance;
			
			//CO2 emission for year 2015
//			double CO2 = tkm*carbonIntensity[commodityIndex][2];
			double vkm = tkm/truckLoad;
			
			//hinterland edge is also assigned with the tkm
			edge.assignTkm_commodity(tkm, commodityIndex);
			edge.assignVkm_commodity(vkm, commodityIndex);
			edge.assignTonnes_commodity(flow, commodityIndex);
			edge.assignCO2Emission(CO2);
			
			//calculation for domestic transport of the main mode (originally only for air and sea, sea2 mode)
			if (edge.getMode().equalsIgnoreCase("Road"))
			{
				//don't assign domestic volume on road if main mode is road
				if(mode==2)
				{
					continue;
				}
				//assign domestic road flow on other mode than road
				edge.assignDomestic_RoadTKM(tkm);
			}

			if (edge.getMode().equalsIgnoreCase("Rail"))
			{
				if(mode==1)
				{
					continue;
				}
				edge.assignDomestic_RailTKM(tkm);
			}
			//TODO:waterways is not taken into account temporarily
		}
	}

	public void setLogitShare(double logitShare) {
		this.logitShare = logitShare;
	}

	public double getLogitShare() {
		return logitShare;
	}
	
	public void setNetwork(MultiModalNetwork network)
	{
		this.network=network;
	}

	public double getDerivativeOriginPort() {
		return derivativeOriginPort;
	}

	public double getDerivativeDestinationPort() {
		return derivativeDestinationPort;
	}

	public void setDerivativeOriginPort(double derivativeOriginPort) {
		this.derivativeOriginPort = derivativeOriginPort;
	}

	public void setDerivativeDestinationPort(double derivativeDestinationPort) {
		this.derivativeDestinationPort = derivativeDestinationPort;
	}

	public void setCosts(double costs) {
		this.costs = costs;
	}

	public double getRailLength() {
		return railLength;
	}	
}
