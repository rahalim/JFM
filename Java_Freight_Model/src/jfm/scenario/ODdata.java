package jfm.scenario;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import au.com.bytecode.opencsv.CSVReader;
import jfm.network.Centroid;


public class ODdata {
	private int columnToUse = 12;  
	private String ODScenario;
	private HashMap<Centroid, HashMap<Centroid, Double>> odFlows = new HashMap<Centroid, HashMap<Centroid,Double>>();
	public  String status="BAU";
			
	private int centroidSize, modeSize, commoditySize, year;

	//from, to, no. of modes
	private double [][][] od_AvTime;
	private double [][][] od_AvDistance;
	private double [][][] od_MinCost;
	
	//from, to
	private double [][][] risk_safety;
	private double [][][] reliability;
	
	//values by commodities for 10 years
	//from, to, commodities, year
	private double [][][][] od_Values;
	
	//from, to, mode, commodity
	private double [][][][] values_modes;
	private double [][][][] values_modes_ref;
	
	//from, to, mode, commodity
	private double [][][][] utility_modes;
	private double [][][][] mode_share;
	private double [][][][] od_Weight;
	
	//OD pair weight before mode split (from, to, commodities, year)
	private double [][][][] od_Weights;
	private double [][][][] od_Weights_ref;
	
	//from, to, mode, commodity
	private double [][][][] od_weight_final;
	private double [][][][] od_weight_ref;	
	
	private double[][][] odFlowByModes;
	public ODdata(String odName,int centroids, int modes, int commodities, int year){
		
		this.centroidSize=centroids;
		this.modeSize=modes;
		this.commoditySize= commodities;
		this.year=year;
		
		System.out.println("odName is: "+odName+" number of centroids: "+centroids
				+" number of modes: "+modes+" number of commodities: "+commodities+" year: "+year);
		
		this.setODScenario(odName);
		
		this.od_AvTime = new double[centroids][centroids][modes];
		this.od_AvDistance = new double[centroids][centroids][modes];
		this.od_MinCost = new double[centroids][centroids][modes];
		
		this.risk_safety = new double [centroids][centroids][modes];
		this.reliability = new double [centroids][centroids][modes];

		this.od_Values = new double[centroids][centroids][commodities][year];
		this.values_modes = new double[centroids][centroids][modes][commodities];
		this.values_modes_ref = new double[centroids][centroids][modes][commodities];
				
		this.od_Weights = new double[centroids][centroids][commodities][year];
		this.od_Weights_ref = new double[centroids][centroids][commodities][year];
		
		//to store the utility value by mode and commodity
		this.utility_modes = new double[centroids][centroids][modes][commodities];
		this.mode_share= new double[centroids][centroids][modes][commodities];
		
		this.od_Weight = new double [centroids][centroids][modes][commodities];
		this.od_weight_final = new double[centroids][centroids][modes][commodities];
		this.od_weight_ref = new double[centroids][centroids][modes][commodities];
		
		this.odFlowByModes = new double[centroids][centroids][modes];
	}
	
	public ODdata(String odFileName, HashMap<String, Centroid> countries){
		this.parseODfile(odFileName, countries);
	}

	//parsing od in csv file
	private void parseODfile(String odFileName, HashMap<String, Centroid> countries){

		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(odFileName), ',','\'', 1);

			String[] nextLine;
			while ((nextLine = reader.readNext()) != null){
				Centroid origin = countries.get(nextLine[0]);
				Centroid destination = countries.get(nextLine[1]);

				if(origin==destination){
					// we ignore flows inside a country
					continue;
				}

				String datum = nextLine[columnToUse];

				if (!datum.trim().isEmpty()){
					/* 
					 * if the datum is an empty string, there is no flow
					 * between the two contries, so we can safely ignore
					 * the entire pair 
					 */ 
					Double flow = Double.parseDouble(datum); 
					this.putODFlows(origin, destination, flow);
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * helper method that is responsible for storing the od data in the 
	 * odData hashmap
	 * @param origin
	 * @param destination
	 * @param flow
	 */
	public void putODFlows(Centroid origin, Centroid destination, double flow){
		HashMap<Centroid, Double> destinationData;
		if(this.odFlows.containsKey(origin)){
			destinationData = this.odFlows.get(origin);
		}else{
			destinationData = new HashMap<Centroid, Double>();
		}
		destinationData.put(destination, flow);
		this.odFlows.put(origin, destinationData);	
	}
	
	
	public void putODFlowsbyModes(int origin, int destination, int mode, double flow){
		this.odFlowByModes[origin][destination][mode] = flow;
	}
	
	public void putODDistance(int origin, int destination, int mode, double distance){
		this.od_AvDistance[origin][destination][mode] = distance;	
	}
	
	public void putODTime(int origin, int destination, int mode, double avTime){
		this.od_AvTime[origin][destination][mode] = avTime;	
	}

	public void putODReliability(int origin, int destination, int mode, double reliability){
		this.reliability[origin][destination][mode] = reliability;		
	}
	
	public void putODCost(int origin, int destination,int mode, double cost){
		this.od_MinCost[origin][destination][mode] = cost;
	}
	
	public void putODRiskSafety(int origin, int destination,int mode, double risk){
		this.risk_safety[origin][destination][mode] = risk;
	}
	
	public double getODTime(int origin, int destination, int mode) {
		return od_AvTime[origin][destination][mode];
	}
	
	public double getODDistance(int origin, int destination, int mode) {
		return od_AvDistance[origin][destination][mode];
	}
	
	public double getODCost(int origin, int destination, int mode) {
		return od_MinCost[origin][destination][mode];
	}	
	

	public double getRisk_safety(int origin, int destination, int mode) {
		return risk_safety[origin][destination][mode];
	}

	public double getReliability(int origin, int destination, int mode) {
		return reliability[origin][destination][mode];
	}

	/**
	 * 
	 * @return available origins
	 */
	public Set<Centroid> getOrigins(){
		return this.odFlows.keySet();
	}

	/**
	 * 
	 * @param origin
	 * @return available destinations given origin
	 */
	public Set<Centroid> getDestinations(Centroid origin){
		return odFlows.get(origin).keySet();
	}

	/**
	 * 
	 * @param origin
	 * @param destination
	 * @return the flow between origin and destination
	 */
	public double getFlow(Centroid origin, Centroid destination){
		return this.odFlows.get(origin).get(destination);
	}


	/**
	 * @return the oDScenario
	 */
	public String getODScenario() {
		return ODScenario;
	}

	/**
	 * @param oDScenario the oDScenario to set
	 */
	public void setODScenario(String oDScenario) {
		ODScenario = oDScenario;
	}

	/**
	 * @return the od_Values
	 */
	public double getOd_Values(int fromID, int toID, int commodity, int year) {
		return od_Values[fromID][toID][commodity][year];
	}

	/**
	 * @param od_Values the od_Values to set
	 */
	public void setOd_Values(int fromID, int toID, int commodity, int year, double val) {
		 this.od_Values[fromID][toID][commodity][year]=val;
	}
	
	public double[] getUtilityByModes(int origin, int destination, int mode){
		return this.utility_modes[origin][destination][mode];
	}
	
	public void setUtilityModes(int origin, int destination, int mode, int commodity, double util){
		this.utility_modes[origin][destination][mode][commodity]=util;
	}

	/**
	 * @return the mode_share
	 */
	public  double[] getMode_share(int origin, int destination,int mode) {
		return mode_share[origin][destination][mode];
	}

	/**
	 * @param mode_share the mode_share to set
	 */
	public void setMode_share(int fromID, int toID, int mode, int commodity, double modeShare) {
		this.mode_share[fromID][toID][mode][commodity] = modeShare;
	}

	public double[] getValuesByModes(int fromID, int toID, int mode) {
		return values_modes[fromID][toID][mode];
	}

	public void setValuesByModes(int fromID, int toID, int mode, int commodity, double valueMode) {
		this.values_modes[fromID][toID][mode][commodity] = valueMode;
	}
	
	public double[] getValuesByModes_ref(int fromID, int toID, int mode) {
		return values_modes_ref[fromID][toID][mode];
	}

	public void setValuesByModes_ref(int fromID, int toID, int mode, int commodity, double valueMode) {
		this.values_modes_ref[fromID][toID][mode][commodity] = valueMode;
	}
	
	public double[] getWeightODByModes(int fromID, int toID, int mode) {
		return od_Weight[fromID][toID][mode];
	}

	public void setWeightOD(int fromID, int toID, int mode, int commodity, double weight) {
		this.od_Weight[fromID][toID][mode][commodity] = weight;
	}
	
	
	public void setWeightODRef(int fromID, int toID, int mode, int commodity, double weight) {
		this.od_weight_ref[fromID][toID][mode][commodity] += weight;
	}
	
	public double getWeightOD_year(int fromID, int toID, int commodity, int yearIndex) {
		
		return this.od_Weights[fromID][toID][commodity][yearIndex];
	}
	
	public void setWeightOD_year(int fromID, int toID, int commodity, double weight, int yearIndex) {
		
			this.od_Weights[fromID][toID][commodity][yearIndex] =weight;
	}
	
	public void setWeightOD_year_ref(int fromID, int toID, int commodity, double weight, int yearIndex) {
		
		this.od_Weights_ref[fromID][toID][commodity][yearIndex] = weight;
		}
	
	public void clearMatrices()
	{
		//clearing all the matrices by creating new ones
		this.values_modes = new double[centroidSize][centroidSize][modeSize][commoditySize];
		this.values_modes_ref = new double[centroidSize][centroidSize][modeSize][commoditySize];
		
		this.utility_modes = new double[centroidSize][centroidSize][modeSize][commoditySize];
		this.mode_share= new double[centroidSize][centroidSize][modeSize][commoditySize];
		
		this.od_Weight = new double[centroidSize][centroidSize][modeSize][commoditySize];
		this.od_weight_final = new double[centroidSize][centroidSize][modeSize][commoditySize];
		this.od_weight_ref = new double[centroidSize][centroidSize][modeSize][commoditySize];
	}	
}
