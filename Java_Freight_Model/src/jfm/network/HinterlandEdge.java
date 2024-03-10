package jfm.network;

import java.util.ArrayList;

import edu.uci.ics.jung.graph.util.Pair;

public class HinterlandEdge extends AbstractEdge {
	

	private PortNode port;
	private Centroid centroid;
	private double transferTime;
	private double costs;
	
	static {
		NetworkComponentFactory.getFactory().registerProduct("HinterlandEdge",HinterlandEdge.class);
	}
	private static final double minLength = 50; //min length of hinterland connection is 50 km
	private double speed;
	private double ValueOfTime=0.196; //$/h.tonnes
	private double additionalTime = 0;
	private int id;
	private double distance=0;
	private double time=0;
	private double roadKM=0;
	private double railKM=0;
	private double roadTKM=0;
	private double railTKM=0;
//	private static  int count=0;
	
	/**
	 */
	public HinterlandEdge(Pair<AbstractNode> endpoints,
			String mode, double distance, double time) {
		super(endpoints, mode);

		for (AbstractNode node : endpoints) 
		{
			if(node.getClass() == PortNode.class)
			{
				this.port = (PortNode)node;				
//				this.count++;
//				System.out.println("port count:"+count);
			}
			if(node.getClass()==Centroid.class){
				this.centroid= (Centroid)node;
			}	
		}
		this.setMode("HinterlandTransport");
		this.transportCostsPerKm=0.57;//$/km
		this.speed=1000; //in km/days
		this.distance=distance;
		this.time=time;	
		this.length = Haversine.HaverSineDistance(endpoints);
	
		updateCost();
	}
	
	/**
	 * 
	 */
	public void updateCost(){
		//this was travel time that was calculated by speed and distance
		//atm we use the travel time from the database
//		double travelTime = this.distance/this.getSpeed()+this.getAdditionalTime();
//		travelTime = Math.max(travelTime, 0); //avoid negative travel time
		
		//added handling cost of the port in the hinterland edge
		if(this.port!=null)
		{
			//value of time and travel time are in $/h.tonnes and h respectively
			this.costs = this.transportCostsPerKm*this.distance + 
					this.ValueOfTime *this.time + super.getToll()
			//TODO:temporarily deactivated to compute cost per mode
					+ this.port.getHandlingCosts();
//			System.out.println("the name of the port: "+port.getLabel()+
//					" the handling cost of the port: "+this.port.getHandlingCosts());
		}
		else
			this.costs = this.transportCostsPerKm*this.distance + 
			this.ValueOfTime * time + super.getToll();
	}
	
	public double getTravelTime()
	{
		//returning value of hinterland travel time (h) as parsed from the database
		return this.time;
	}
	
	/**
	 * 
	 * @return
	 */
	public double getAdditionalTime(){
		return this.additionalTime;
	}

	/**
	 * 
	 */
	public void setToll(double toll){
		super.setToll(toll);
		updateCost();
	}
	
	/**
	 * 
	 * @param additionalTime
	 */
	public void setAdditionalTime(double additionalTime){
		this.additionalTime = additionalTime;
		this.updateCost();
	}
	
	/**
	 * returning haversince distance
	 */
	public double getLength(){
		return super.getLength();
	}

	public PortNode getPort() {
		return this.port;
	}
	
	public void setHandlingCost(double handlingCost)
	{
//		System.out.println("updating the port cost");
		this.port.setHandlingCosts(handlingCost);
		updateCost();
	}
	
	public double getCosts()
	{
		return this.costs;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public double getRoadKM() {
		return roadKM;
	}

	public void setRoadKM(double roadKM) {
		this.roadKM = roadKM;
	}

	public double getRailKM() {
		return railKM;
	}

	public void setRailKM(double railKM) {
		this.railKM = railKM;
	}

	public double getRoadTKM() {
		return roadTKM;
	}

	public void setRoadTKM(double roadTKM) {
		this.roadTKM = roadTKM;
	}

	public double getRailTKM() {
		return railTKM;
	}

	public void setRailTKM(double railTKM) {
		this.railTKM = railTKM;
	}

	public double getTime() {
		return time;
	}

	public void setTime(double time) {
		this.time = time;
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}

	public Centroid getCentroid() {
		return centroid;
	}

	public void setCentroid(Centroid centroid) {
		this.centroid = centroid;
	}

}
