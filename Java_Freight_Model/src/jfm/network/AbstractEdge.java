package jfm.network;


import java.util.ArrayList;

import edu.uci.ics.jung.graph.util.Pair;

public abstract class AbstractEdge {
	
	protected String mode;
	private Pair<AbstractNode> endpoints;
	
	//length here is calculated using haversine distance
	protected double length;
	private double toll;
	private double speed;
	
//	public static final double valueOfTime = 3.042;//in dollar/h.TEU or 73 dollar/day.TEU
	
	//for land based transport: road,rail
	public static final double valueOfTime = 0.196; //dollar/h.tonnes	
//	public static final double valueOfTime = 73.0; 
	
	protected double transportCostsPerKm;
	private int Rindex;
	protected String ISO;
		
	//flow attributes
	protected double assignedFlow = 0;
	//container type
	private double weightCategories[]= new double[5];

	private double transshipment=0;

	private double domestic_RoadTKM=0;
	private double domestic_RailTKM=0;
	private double domestic_RiverTKM=0;

	private double []tkm_commodity=new double [25];
	private double []vkm_commodity=new double [25];
	private double []tonnes_commodity =new double[25];
	private double CO2Emission;
	
//	private LoadComposition load= new LoadComposition();	

	// Collection Variables for equilibrium assignment
//	private ArrayList <LoadComposition> load_iterations = new ArrayList<LoadComposition>();
//	private ArrayList <LoadComposition> load_year = new ArrayList<LoadComposition>();

	
	//TODO there appears to be different values of time for different good types
	/*
	 * the logical solution would be to first instantiate the od data
	 * here you know which good type you are interested in. You can
	 * then modify the value of time based on that. In this way, you 
	 * build your network and calculate your cheapest routes on the network
	 * conditions on the value of time of the goodstype for which you
	 * are performing the analysis.
	 * 
	 * The other option would be to implement all values of time and
	 * make cost a vector/list of length goodtypes. Through the transformer
	 * object that is used in the cheapest cost calculation, you can than
	 * select the correct index in this vector/list based on the goodstype
	 * for which you want to do the analysis.
	 * 
	 */
	//for edges other than maritime edge and hinterland edge
	public AbstractEdge(Pair<AbstractNode> endpoints){
		this.endpoints = endpoints;
		this.length = Haversine.HaverSineDistance(endpoints);
//		this.speed=70;
	}
	
	public AbstractEdge(Pair<AbstractNode> endpoints, String mode){
		
		this.endpoints = endpoints;
		this.mode=mode;
		this.length = Haversine.HaverSineDistance(endpoints);
//		this.speed=70; //km/h default value, each mode has its own speed specification
	}

	/**
	 * reset all assigned flow to 0;
	 */
	public void resetAssignedFlow(){
		this.assignedFlow = 0;
		this.domestic_RailTKM=0;
		this.domestic_RoadTKM=0;
		this.domestic_RiverTKM=0;
		
		//resetting the weight of different cargo types
		for (int i = 0; i < weightCategories.length; i++) {
			weightCategories[i]=0;
		}
		
		//resetting the tkm and vkm of all commodities
		for (int i=0; i<this.tkm_commodity.length; i++)
		{
			this.setTkm_commodity(0, i);
			this.setVkm_commodity(0, i);
		}		
	}
	
	/**
	 * 
	 * @return
	 */
	public Pair<AbstractNode> getEndpoints() {
		return endpoints;
	}

	/**
	 * 
	 * @return
	 */
	public double getLength() {
		return length;
	}
	
	/**
	 * 
	 * @return
	 */
	public double getToll() {
		return toll;
	}
	
	/**
	 * 
	 * @param toll
	 */
	public void setToll(double toll) {
		this.toll = toll;
	}
	
	/**
	 * 
	 * @return
	 */
	public double getTransportCostsPerKm () {
		return this.transportCostsPerKm;
	}
	
	/**
	 * 
	 * @return
	 */
	public void setTransportCostsPerKm(double transportCostsPerKm) {
		this.transportCostsPerKm = transportCostsPerKm;
	}
	
	
	public void setTkm_commodity(double[] tkm_commodity) {
		this.tkm_commodity = tkm_commodity;
	}

	public void setVkm_commodity(double[] vkm_commodity) {
		this.vkm_commodity = vkm_commodity;
	}
	
	/**
	 * 
	 * @return
	 */
	public double getTravelTime(){
		return this.getLength() /this.speed;
	}	
	
	
	/**
	 * 
	 * @return the assigned flow
	 */
	public double getAssignedflow(){
		return this.assignedFlow;
	}	
	
	public abstract double getCosts();
	
	public void addAssignedFlow(double assignedFlow) {
		this.assignedFlow += assignedFlow;
	}
	
//	public LoadComposition getLoad()
//	{
//		return this.load;
//	}

	public String getMode() {
		return mode;
	}
	
	public void setMode(String mode) {
		this.mode=mode;
	}
	
	/**
	 * @return the transshipment
	 */
	public double getTransshipment() {
		return transshipment;
	}

	/**
	 * @param transshipment the transshipment to set
	 */
	public void setTransshipment(double transshipment) {
		this.transshipment = transshipment;
	}

	/**
	 * @return the vkm_commodity
	 */
	public double [] getVkm_commodity() {
		return vkm_commodity;
	}

	public void setVkm_commodity(double vkm, int index) {
		this.vkm_commodity[index] = vkm;
	}
	/**
	 * @param vkm_commodity the vkm_commodity to assign
	 */
	public void assignVkm_commodity(double vkm, int index) {
		this.vkm_commodity[index] += vkm;
	}

	/**
	 * @return the tkm_commodity
	 */
	public double [] getTkm_commodity() {
		return tkm_commodity;
	}
	
	public void setTkm_commodity(double tkm, int index) {
		this.tkm_commodity[index] = tkm;
	}

	/**
	 * @param tkm_commodity the tkm_commodity to assign
	 */
	public void assignTkm_commodity(double tkm, int index) {
		this.tkm_commodity[index] += tkm;
	}
	
	public void assignCO2Emission(double CO2) {
		this.CO2Emission+=CO2;
	}
	
	public double getCO2(){
		return this.CO2Emission;
	}

	public double[] getWeightCategories() {
		return weightCategories;
	}

	public void assignWeightByCategories(double weight, int index) {
		this.weightCategories[index] += weight;
	}

	public double getDomestic_RoadTKM() {
		return domestic_RoadTKM;
	}

	public void assignDomestic_RoadTKM(double domestic_RoadTKM) {
		this.domestic_RoadTKM += domestic_RoadTKM;
	}

	public double getDomestic_RailTKM() {
		return domestic_RailTKM;
	}

	public void assignDomestic_RailTKM(double domestic_RailTKM) {
		this.domestic_RailTKM += domestic_RailTKM;
	}


	public double getDomestic_RiverTKM() {
		return domestic_RiverTKM;
	}

	public void assignDomestic_RiverTKM(double domestic_RiverTKM) {
		this.domestic_RiverTKM += domestic_RiverTKM;
	}

	/**
	 * @return the rindex
	 */
	public int getRindex() {
		return Rindex;
	}

	/**
	 * @param rindex the rindex to set
	 */
	public void setRindex(int rindex) {
		Rindex = rindex;
	}

	public String getISO() {
		return ISO;
	}

	public void setISO(String iSO) {
		ISO = iSO;
	}

	public double[] getTonnes() {
		return tonnes_commodity;
	}

	public void assignTonnes_commodity(double tonnes, int commodityIndex) {
		this.tonnes_commodity[commodityIndex] += tonnes;
	}

}
