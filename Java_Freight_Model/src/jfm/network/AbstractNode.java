package jfm.network;

import java.util.ArrayList;


public class AbstractNode {
	private double lat;
	private double lon;
	private String name;
	private int id;
	private double[] capacity = new double[5];
	private double[] cost = new double[5];
	private double[] cost_ref = new double [5];
	private double load;
	private double load_final;
	private double max_distance;
	protected double[] handlingTime= new double [5];;
	protected double[] handlingTime_ref= new double [5];
	protected double transhipmentTime;
	private ArrayList<Double> expansion;
	private boolean active;
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	public AbstractNode(double lat, double lon, String name, int id){
		this.lat = lat;
		this.lon = lon;
		this.name = name;
		this.id = id;
		this.expansion= new ArrayList<Double>();
		//TODO: this has to be specified by the node type
		//centroid should not have handling time
		//if the shortest path goes via centroid then please check schematization
		this.transhipmentTime=0;
	}
	
	public AbstractNode(double lat, double lon, String name, int id, double[] handlingTime){
		this.lat = lat;
		this.lon = lon;
		this.name = name;
		this.id = id;
		this.expansion= new ArrayList<Double>();
		this.handlingTime=handlingTime;
	}
	
	/**
	 * @return the lat
	 */
	public double getLat() {
		return lat;
	}
	
	public void setLat(double lat)
	{
		this.lat=lat;
	}
	
	public void setLon(double lon)
	{
		this.lon=lon;
	}
	/**
	 * @return the lon
	 */
	public double getLon() {
		return lon;
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the load
	 */
	public double getLoad() {
		return load;
	}

	/**
	 * @param load the load to set
	 */
	public void setLoad(double load) {
		this.load = load;
	}

	/**
	 * @return the load_final
	 */
	public double getLoad_final() {
		return load_final;
	}

	/**
	 * @param load_final the load_final to set
	 */
	public void setLoad_final(double load_final) {
		this.load_final = load_final;
	}

	/**
	 * @return the active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * @param active the active to set
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * @return the max_distance
	 */
	public double getMax_distance() {
		return max_distance;
	}

	/**
	 * @param max_distance the max_distance to set
	 */
	public void setMax_distance(double max_distance) {
		this.max_distance = max_distance;
	}



	/**
	 * @return the capacity
	 */
	public double[] getCapacity() {
		return capacity;
	}

	/**
	 * @param capacity the capacity to set
	 */
	public void setCapacity(double[] capacity) {
		this.capacity = capacity;
	}

	/**
	 * @return the cost
	 */
	public double[] getCost() {
		return cost;
	}

	/**
	 * @param cost the cost to set
	 */
	public void setCost(double[] cost) {
		this.cost = cost;
	}

	/**
	 * @return the cost_ref
	 */
	public double[] getCost_ref() {
		return cost_ref;
	}

	/**
	 * @param cost_ref the cost_ref to set
	 */
	public void setCost_ref(double[] cost_ref) {
		this.cost_ref = cost_ref;
	}

	/**
	 * @return the handlingTime
	 */
	public double[] getHandlingTime() {
		return handlingTime;
	}

	/**
	 * @param handlingTime the handlingTime to set
	 */
	public void setHandlingTime(double[] handlingTime) {
		this.handlingTime = handlingTime;
	}

	/**
	 * @return the handlingTime_ref
	 */
	public double[] getHandlingTime_ref() {
		return handlingTime_ref;
	}

	/**
	 * @param handlingTime_ref the handlingTime_ref to set
	 */
	public void setHandlingTime_ref(double[] handlingTime_ref) {
		this.handlingTime_ref = handlingTime_ref;
	}

	/**
	 * @return the expansion
	 */
	public ArrayList<Double> getExpansion() {
		return expansion;
	}

	/**
	 * @param expansion the expansion to set
	 */
	public void setExpansion(ArrayList<Double> expansion) {
		this.expansion = expansion;
	}
	
	public void setName (String name)
	{
		this.name=name;
	}
	
	public void setID (int id)
	{
		this.id=id;
	}

	public double getTranshipmentTime() {
		return transhipmentTime;
	}

	public void setTranshipmentTime(double transhipmentTime) {
		this.transhipmentTime = transhipmentTime;
	}
}
