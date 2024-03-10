package jfm.network;

import edu.uci.ics.jung.graph.util.Pair;

public class MaritimeEdge extends AbstractEdge {
	static {
		NetworkComponentFactory.getFactory().registerProduct("MaritimeEdge",MaritimeEdge.class);
	}

	private double additionalTime;
	private double ptpDistance;
	private double speed;
//	private double MaritimeValueofTime=4.867; //$/tonnes.day
	private double MaritimeValueofTime=73; //$/TEU.day
	
	/**
	 * 
	 * @param endpoints
	 * @param toll
	 * @param additionalTime
	 */
	public MaritimeEdge(Pair<AbstractNode> endpoints, double toll,
			double additionalTime, double ptpDistance, int index, String mode) 
	{
		//instantiating abstract edge and passing the argument back
		super(endpoints, mode);
//		this.transportCostsPerKm =0.0016;//$/Tkm, original value
		this.transportCostsPerKm =0.0048;
//		this.transportCostsPerKm =0.025;//$/Tkm
//		this.transportCostsPerKm =0.075;//$/Tkm
		this.speed=1000.0; //taken from the straight from the WCM in km/days
		this.additionalTime = additionalTime;
		this.ptpDistance=ptpDistance;

		this.length = Haversine.HaverSineDistance(endpoints);
		this.setRindex(index);
	}

	/**
	 * @return the additionalTime
	 */
	public double getAdditionalTime() {
		return additionalTime;
	}

	/**
	 * @return the additionalTime
	 */
	public void setAdditionalTime(double additionalTime) {
		this.additionalTime = additionalTime;
	}
	

	public double getCosts(){
//		double time =  this.getTravelTime() + this.getAdditionalTime();	
		
		double costs = this.transportCostsPerKm*this.getLength()+
				//travel time is in days and value of time is therefore also in $/days
				//$/day * km *1 day /1000 km
			    this.MaritimeValueofTime * this.getLength()/this.speed;	
		return costs;
	}
	
	//this has to be adjusted in such a way it uses the shortest port to port distance
	public double getLength(){		
		//when normal network is used, then this function should not return the distance table
		//returning the haversine distance
		return this.length;
	}
	
	//returning travel time in hours since edges of the other modes are measured in hours
	public double getTravelTime()
	{
		//travel time in hours km * 1 day/1000 km * 24 hours/day
		return this.getLength()/this.speed*24;
	}
}
