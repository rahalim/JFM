package jfm.network;


import java.util.ArrayList;

import edu.uci.ics.jung.graph.util.Pair;

public class RoadEdge extends AbstractEdge {
	

	/**
	 * Time which is required to pass this road, with speed limit and road throughput limit applied
	 */
	private double trip_time;
	private double speed;
	private String region;
	private String mode;
	private int edgeID;
	
//	public double valueOfTime = 3.042;//in dollar/h.TEU or 73 dollar/day.TEU
	private double ValueOfTime=0.196;// $/tonnes.h
	
	private double roadRealDistance;
	private double transloading_cost;
//	private LoadComposition load =new LoadComposition();;
//	private LoadComposition load_final;
	private int FromNodeId;
	private int ToNodeId;

	// Collection Variables
//	private ArrayList <LoadComposition> load_iterations = new ArrayList<LoadComposition>();
//	private ArrayList <LoadComposition> load_year = new ArrayList<LoadComposition>();

	static {
		NetworkComponentFactory.getFactory().registerProduct("RoadEdge",RoadEdge.class);
	}

	public RoadEdge(Pair<AbstractNode> endpoints, int id, String mode, double speed, double time) {
		super(endpoints, mode);
		this.edgeID= id;
		//trip time is set by the method, transport cost is dependent upon mode and geographical location
		this.mode=mode;
		//default
//		this.trip_time=time;
//		this.speed=70; //70 km/hour as a standard
//		this.roadRealDistance = distance;
		
		//test of realism
		this.roadRealDistance = Haversine.HaverSineDistance(endpoints);
		this.trip_time=roadRealDistance/speed;	
		
		if(mode.equalsIgnoreCase("Road_Station_Connector"))
		{
			this.trip_time=time;
//			System.out.println("road station connector is created,from, to, time:"
//			+endpoints.getFirst().getId()+","+endpoints.getSecond().getId()+","+time);
		}
		
		if(mode.equalsIgnoreCase("Station_Rail_Connector"))
		{
			this.trip_time=time;
//			System.out.println("station rail connector is created,from, to, time:"
//					+endpoints.getFirst().getId()+","+endpoints.getSecond().getId()+","+time);
		}
	}
	
	@Override
	public double getCosts() {
		
		double time =  this.getTripTime();
		double multiplier = 1.0;
		if(region==null)
		{
			multiplier =1.0;
		}
		
		//transport cost by mode 
		if(mode.equalsIgnoreCase("Road 2 lanes"))
		{
			this.transportCostsPerKm=0.043*multiplier;	
			time=this.getTripTime();
		}
		if(mode.equalsIgnoreCase("Railway"))
		{
//			//baseline
			double railtransportCost = 0.026;
			double VAT = railtransportCost *0.1;
			double TAC = railtransportCost *0.55;
			double netCost = railtransportCost*0.35;
			this.transportCostsPerKm= TAC+VAT+netCost;
			
			//basic
//			double railtransportCost = 0.026;
//			double VAT = railtransportCost *0.1;
//			double TAC = railtransportCost *0.45;
//			double netCost = railtransportCost*0.35;
////			this.transportCostsPerKm= TAC+VAT+netCost;	
//			this.transportCostsPerKm= 0.95*(TAC+VAT+netCost);
			
			//moderate
//			double railtransportCost = 0.026;
//			double VAT = 0;
//			double TAC = railtransportCost *0.35;
//			double netCost = railtransportCost*0.35;
////			this.transportCostsPerKm= TAC+VAT+netCost;
//			this.transportCostsPerKm= 0.925*(TAC+VAT+netCost);
			
			//strong
//			double railtransportCost = 0.026;
//			double VAT = 0;
//			double TAC = railtransportCost *0.25;
//			double netCost = railtransportCost*0.35;
////			this.transportCostsPerKm= TAC+VAT+netCost;			
//			this.transportCostsPerKm= 0.9*(TAC+VAT+netCost);
		}
		
		if(mode.equalsIgnoreCase("Air"))
		{
			this.transportCostsPerKm=0.37;
		}
		
		if(mode.equalsIgnoreCase("Connector"))
		{
			this.transportCostsPerKm=0.15;
		}
		
		if(mode.equalsIgnoreCase("Handling"))
		{
			this.transportCostsPerKm=1.5;
		}
		
		//waiting cost
		if(mode.equalsIgnoreCase("Road_Station_Connector"))
		{
			this.transportCostsPerKm=1.5;
		}
		//transloading cost
		if(mode.equalsIgnoreCase("Station_Rail_Connector"))
		{
			this.transportCostsPerKm=1.5;
			//base scenario
			this.transloading_cost=2.619; //$/ton
			
			//basic ambition
//			this.transloading_cost=2.619*0.7;
			
			//moderate ambition
//			this.transloading_cost=2.619*0.6;
			
			//strong ambition
//			this.transloading_cost=2.619*0.5;		
		}
		//UPDATE costs to include other modes
		double costs = this.transportCostsPerKm*this.roadRealDistance + this.transloading_cost
				+ ValueOfTime * time;
		
//		System.out.println("mode,cost per km, distance, distanceCost, VoT, time, timeCost, total cost "
//		+this.getMode()+" ,"
//		+this.transportCostsPerKm+" ,"+this.roadRealDistance+','+transportCostsPerKm*roadRealDistance+","
//		+valueOfTime+","+time+","+valueOfTime*time+","+costs);		
		return costs;
	}
	
	public double getTripTime()
	{
		return this.trip_time;
	}
	
	//method overwriting from abstract edge, special for road edge to return travel time in hours
	public double getTravelTime()
	{
		if(this.trip_time==0)
		{
			//distance/speed, only if trip time is not available because of database error
			return this.roadRealDistance/this.speed;
		}
		else
			//normal case
		{
			//waiting cost and cost to reach the dry port
			if(mode.equalsIgnoreCase("Road_Station_Connector"))
			{
				//base
				this.trip_time =4;
				
				//basic -0.25%
//				this.trip_time =3;
				
				//moderate -50%
//				this.trip_time =2;
				
				//strong -75%
//				this.trip_time =1;
			}
			
			//transloading cost
			if(mode.equalsIgnoreCase("Station_Rail_Connector"))
			{
//				//base
				this.trip_time =4;
				
				//basic
//				this.trip_time =3;
				
				//moderate -50%
//				this.trip_time =2;
				
				//strong-75%
//				this.trip_time =1;	
			}
			return this.trip_time;
		}
	}

	public void setTripTime(double time)
	{
		this.trip_time= time;
	}

	public double getRoadRealDistance() {
		return roadRealDistance;
	}
	
	public double getLength() {
		return roadRealDistance;
	}

	public void setRoadRealDistance(double roadRealDistance) {
		this.roadRealDistance = roadRealDistance;
	}
}
