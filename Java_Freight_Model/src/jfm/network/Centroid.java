package jfm.network;

import java.util.HashMap;
import java.util.Set;



public class Centroid extends AbstractNode {
	private String region;
	private String ISOAlpha3;
	private double factor; 
	private double[] GDP;
	private double[] GDP_capita;
	private double [] GDP_percent =new double [9];
    private double [] GDP_capita_percent =new double [9];
    private int air_count, rail_count, road_count, sea_count,sea2_count, waterways_count;
    private PortNode nearestStation;
    
	
	//hashmap containing the the port and the hinterland connections
	private HashMap<PortNode, HinterlandEdge> ports = new HashMap<PortNode, HinterlandEdge>();
	
	//if there is more than one edge connecting centroid to multiple nearest stations
	private HashMap<PortNode, RoadEdge> stations = new HashMap<PortNode, RoadEdge>();
	
	private static HashMap<String, ContinentCostMultiplier> continentMultiplierMap = new HashMap<String, Centroid.ContinentCostMultiplier>();
	private ContinentCostMultiplier multiplier;
	
	static {
		NetworkComponentFactory.getFactory().registerProduct("CountryNode",Centroid.class);
		continentMultiplierMap.put("Africa", ContinentCostMultiplier.AFRICA);
		continentMultiplierMap.put("Americas", ContinentCostMultiplier.AMERICAS);
		continentMultiplierMap.put("Asia", ContinentCostMultiplier.ASIA);
		continentMultiplierMap.put("Oceania", ContinentCostMultiplier.OCEANIA);
		continentMultiplierMap.put("Europe", ContinentCostMultiplier.EUROPE);
	}	
	
	public enum ContinentCostMultiplier{
		AFRICA(4.0), AMERICAS(1.0), ASIA(3.0), OCEANIA(2.0), EUROPE(1.0);
		
		private double multiplier;
		
		private ContinentCostMultiplier(double multiplier){
			this.multiplier = multiplier;
		}
		
		public double getMultiplier(){
			return this.multiplier;
		}
	}
	
	/**
	 * 
	 * @param name the country name
	 * @param ISOAlpha3 iso alpha 3 code
	 * @param lat latitude of capital
	 * @param lon longitude of capital
	 * @param id id used in WCM
	 * 
	 * 
	 */
	public Centroid(String name, String ISOAlpha3, String province, double lat, double lon, 
			int id){
		super(lat, lon, name, id);
		this.ISOAlpha3 = ISOAlpha3;
		this.region = province;
		this.multiplier = Centroid.continentMultiplierMap.get(this.region);
	}
	
	public Centroid(String name, String ISOAlpha3, String region, double lat, double lon, 
			int id, double factor, double[] GDP, double[] GDP_capita){
		super(lat, lon, name, id);
		this.GDP=GDP;
		this.GDP_capita=GDP_capita;
		this.ISOAlpha3 = ISOAlpha3;
		this.region = region;
		this.factor =factor;
		this.multiplier = Centroid.continentMultiplierMap.get(this.region);
		this.air_count=0;
		this.sea_count=0;
		this.sea2_count=0;
		this.road_count=0;
		this.rail_count=0;
		this.waterways_count=0;
	}
	
	/**
	 * 
	 * @return
	 */
	public ContinentCostMultiplier getMultiplier(){
		return this.multiplier;
	}

	/**
	 * 
	 * @return
	 */
	public String getRegion(){
		return this.region;
	}
	
	/**
	 * 
	 * @param port
	 * @return the hinterland edge connecting the port and the country
	 */
	public AbstractEdge getHinterlandConnection(PortNode port){
		return this.ports.get(port);
	}
	
	/**
	 * @return the ISOAlpha3
	 */
	public String getISOAlpha3() {
		return this.ISOAlpha3;
	}
	
	/**
	 * 
	 * @return ports connected to country
	 */
	public Set<PortNode> getPorts(){
		return this.ports.keySet();
	}
	
	/**
	 * add a port to the list of ports that can be reached from this country
	 * @param port
	 */
	public void addHinterlandConnection(PortNode port, HinterlandEdge edge){
		this.ports.put(port, edge);
	}
	
	/**
	 * add a port to the list of ports that can be reached from this country
	 * @param port
	 */
	public void addCentroidtoStationConnection(PortNode port, RoadEdge edge){
		this.stations.put(port, edge);
	}


	public PortNode getNearestStation() {
		return nearestStation;
	}

	public void setNearestStation(PortNode nearestStation) {
		this.nearestStation = nearestStation;
	}

	/**
	 * @return the factor
	 */
	public double getFactor() {
		return factor;
	}

	/**
	 * @param factor the factor to set
	 */
	public void setFactor(double factor) {
		this.factor = factor;
	}

	/**
	 * @return the gDP
	 */
	public double[] getGDP() {
		return GDP;
	}

	/**
	 * @param gDP the gDP to set
	 */
	public void setGDP(double[] gDP) {
		GDP = gDP;
	}

	/**
	 * @return the gDP_capita
	 */
	public double[] getGDP_capita() {
		return GDP_capita;
	}

	/**
	 * @param gDP_capita the gDP_capita to set
	 */
	public void setGDP_capita(double[] gDP_capita) {
		GDP_capita = gDP_capita;
	}

	/**
	 * @return the gDP_percent
	 */
	public double [] getGDP_percent() {
		return GDP_percent;
	}

	/**
	 * @param gDP_percent the gDP_percent to set
	 */
	public void setGDP_percent(double [] gDP_percent) {
		GDP_percent = gDP_percent;
	}

	/**
	 * @return the gDP_capita_percent
	 */
	public double [] getGDP_capita_percent() {
		return GDP_capita_percent;
	}

	/**
	 * @param gDP_capita_percent the gDP_capita_percent to set
	 */
	public void setGDP_capita_percent(double [] gDP_capita_percent) {
		GDP_capita_percent = gDP_capita_percent;
	}

	/**
	 * @return the sea_count
	 */
	public int getSea_count() {
		return sea_count;
	}

	/**
	 * @param sea_count the sea_count to set
	 */
	public void setSea_count(int sea_count) {
		this.sea_count = sea_count;
	}

	/**
	 * @return the air_count
	 */
	public int getAir_count() {
		return air_count;
	}

	/**
	 * @param air_count the air_count to set
	 */
	public void setAir_count(int air_count) {
		this.air_count = air_count;
	}

	/**
	 * @return the road_count
	 */
	public int getRoad_count() {
		return road_count;
	}

	/**
	 * @param road_count the road_count to set
	 */
	public void setRoad_count(int road_count) {
		this.road_count = road_count;
	}

	/**
	 * @return the waterways_count
	 */
	public int getWaterways_count() {
		return waterways_count;
	}

	/**
	 * @param waterways_count the waterways_count to set
	 */
	public void setWaterways_count(int waterways_count) {
		this.waterways_count = waterways_count;
	}

	/**
	 * @return the rail_count
	 */
	public int getRail_count() {
		return rail_count;
	}

	/**
	 * @param rail_count the rail_count to set
	 */
	public void setRail_count(int rail_count) {
		this.rail_count = rail_count;
	}

	/**
	 * @return the sea2_count
	 */
	public int getSea2_count() {
		return sea2_count;
	}

	/**
	 * @param sea2_count the sea2_count to set
	 */
	public void setSea2_count(int sea2_count) {
		this.sea2_count = sea2_count;
	}
	
}
