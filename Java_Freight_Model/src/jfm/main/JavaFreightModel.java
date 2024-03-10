/**
 * JavaFreightModel.java
 *
 * @author Ronald A. Halim
 * @version 1.0
 * This implementation of International Freight Model is based on the original model developed by Ronald Apriliyanto Halim during 
 * his work at the OECD/ITF" 
 *     
 */

package jfm.main;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import jfm.choiceset.ChoiceNetwork;
import jfm.gui.JFMVisualizationMap;
import jfm.models.Assignment;
import jfm.models.Assignment_Update;
import jfm.models.ComputeWeightODLogSum;
import jfm.models.Logit;
import jfm.models.ModeShare;
import jfm.models.ValueToWeight;
import jfm.network.AbstractEdge;
import jfm.network.Centroid;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.network.PortNode;
import jfm.output.OutputWriter;
import jfm.scenario.ODdata;
import jfm.scenario.ScenarioInterface;

public class JavaFreightModel {
		
	//sql server authentication in the localhost machine (MacOS)
	

	//filepath for mode share, transport cost table, and transit OD flow
	private String ValueWeightOutputFilePath= "output/ValuetoWeightOutput/ValueWeightOutput_LogSum";
	private String costByModesPath="output/costByModes";
	private String ODflowsPath ="output/ODflows/ODTransit";
	
	//filepath for output of assignment
	private String MaritimeEdgeOutputFilePath= "output/MaritimeEdges";
	private String AirEdgeOutputFilePath= "output/AirEdges";
	private String RailEdgeOutputFilePath= "output/RailEdges";
	private String RoadEdgeOutputFilePath= "output/RoadEdges";
	
	private String TKMPath ="output/modeTKM";
	private String ChoiceSetDir ="output/ChoiceSetInfo";
	private String portOutputpath ="output/PortThroughput";
	
	private Statement stm;
	private Connection conn;
	public static long startTime;
	
//	private int[] years= {2020,2025,2030};
	private int[] years= {2015,2020,2025,2030};
//	private int[] years= {2025,2030};
//	private int[] years= {2015};
//	private int[] years= {2050};
//	private int[] years= {2030};
		
	static
	{
		try
		{
			Class.forName("jfm.network.HinterlandEdge");
			Class.forName("jfm.network.Centroid");
			Class.forName("jfm.network.MaritimeNode");
			Class.forName("jfm.network.PortNode");
			Class.forName("jfm.network.MaritimeEdge");
			Class.forName("jfm.network.AbstractServiceEdge");
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");  
		}
		catch (ClassNotFoundException any)
		{
			any.printStackTrace();
		}
	}

	//main model routine
	//TODO:here you can specify additional directories for additional input or output
	public JavaFreightModel(String workingDir) throws Exception
	{
		startTime = System.currentTimeMillis();
		//connecting to sql database, perhaps it's not necessary at this stage
//		connectToDatabase(db_file_name_prefix);		
		//run the model
		executeScenario(null, this.years);
	}
	
	
	public void executeScenario(ScenarioInterface scenario,int[] years) throws SQLException, IOException
	{	
		int baseYear=2010;
		// year of execution: every 5 years from 2010 till 2050: 9 years or 3 years 2015,2030,2050
		//1. parse all the network properties from the database and construct all the objects
		MultiModalNetworkCostNoSQL freightNetwork = new MultiModalNetworkCostNoSQL();
		freightNetwork.buildMultimodalNetwork(this.startTime);
				
		OutputWriter writer = new OutputWriter();
		long endTime;
		long processTime;
		
		//limitation on equilibrium assignment, currently being used to set number of maps drawn
		int iteration_eq=1;
		
		//to store modal shares
		double[] weightRail =new double[years.length];
		double[] weightRoad_HDV =new double[years.length];
		double[] weightRoad_MDV =new double[years.length];
		double[] weightRoad_LDV =new double[years.length];
		
		double[] tkmRail =new double[years.length];
		double[] tkmRoad_HDV =new double[years.length];
		double[] tkmRoad_MDV =new double[years.length];
		double[] tkmRoad_LDV =new double[years.length];
		
		for (int i=0; i<years.length; i++)
		{
			int yearIndex = Math.round((years[i]-baseYear)/5);
			
			//2. parse all the OD flow scenarios 
			ODdata OD=freightNetwork.getBaseOD();
			
			//compute the attributes needed for the mode share model:distance, time, costs, risk, reliability
			//create a choice network, compute the shortest path and set OD data.
			//creating choice network for rail and updating costs matrices
			System.out.println("creating choice sets and computing costs");
			ChoiceNetwork railChoiceNetwork = new ChoiceNetwork(freightNetwork, 0);
			
//			System.out.println("creating choice set for road network HDV");
			ChoiceNetwork road_HDVChoiceNetwork = new ChoiceNetwork(freightNetwork, 1);
			
//			System.out.println("creating choice set for road network MDV");
			ChoiceNetwork road_MDVChoiceNetwork = new ChoiceNetwork(freightNetwork, 3);
			
//			System.out.println("creating choice set for road network LDV");
			ChoiceNetwork road_LDVChoiceNetwork = new ChoiceNetwork(freightNetwork, 4);
			
//			System.out.println("creating choice set for maritime network");
			ChoiceNetwork seaChoiceNetwork = new ChoiceNetwork(freightNetwork, 2);
			
			//setting up the mode share module
			ComputeWeightODLogSum weightOD = new ComputeWeightODLogSum(freightNetwork, yearIndex, this.startTime);
			
			//resetting the flows on the network before next year's assignment procedure
			freightNetwork.resetRoadNetworkGraph();
			freightNetwork.resetRailNetworkGraph();
		
			//each of the iteration updates the cost matrice which is used to compute the mode share
			for (int it=0; it<iteration_eq; it++)
			{
//				System.out.println("performing assignment for iteration: "+it+" cost status: "+OD.status);
				//3. compute the mode share for the traded values and convert them into freight tonnage
				weightOD.compute(yearIndex, freightNetwork);
				
				//just printing the weight for different modes
				System.out.println("Printing value to weight output for: "+years[i]);
				System.out.println("printing weight");
				weightRail[i] =writer.printWeightOutput(freightNetwork,0,years[i]);
				weightRoad_HDV[i] =writer.printWeightOutput(freightNetwork,1,years[i]);
				weightRoad_MDV[i] =writer.printWeightOutput(freightNetwork,3,years[i]);
				weightRoad_LDV[i] =writer.printWeightOutput(freightNetwork,4,years[i]);
				
				double modalShareRail= weightRail[i]/(weightRail[i]+weightRoad_HDV[i]+weightRoad_MDV[i]+weightRoad_LDV[i])*100;
				double modalShareRoad_HDV= weightRoad_HDV[i]/(weightRail[i]+weightRoad_HDV[i]+weightRoad_MDV[i]+weightRoad_LDV[i])*100;
				double modalShareRoad_MDV= weightRoad_MDV[i]/(weightRail[i]+weightRoad_HDV[i]+weightRoad_MDV[i]+weightRoad_LDV[i])*100;
				double modalShareRoad_LDV= weightRoad_LDV[i]/(weightRail[i]+weightRoad_HDV[i]+weightRoad_MDV[i]+weightRoad_LDV[i])*100;
				
				System.out.println ("MODAL SHARE rail and road HDV, road MDV, road LDV:" 
				+modalShareRail+","+modalShareRoad_HDV+","+modalShareRoad_MDV+","+modalShareRoad_LDV);
				
//				writer.printWeightOutput(freightNetwork, 2,years[i]);

				int processors = Runtime.getRuntime().availableProcessors();
//				System.out.println("number of available processor: "+processors);
				
//				=============================rail assignment
				java.lang.System.out.println("Assigning flow on the rail network for year "+years[i]);			
				Assignment_Update railAssignment = new Assignment_Update(freightNetwork,railChoiceNetwork,0,OD,false,false);
				
				endTime=System.currentTimeMillis();
				processTime= endTime-startTime;
//				System.out.println("Assignment for rail mode is done, time: "+processTime+" ms");
				
				System.out.println("printing TKM rail");
				tkmRail[i]=writer.printTKMOutput(freightNetwork, years[i],0, TKMPath);	
				
				//printing volume assigned on each edge
//				java.lang.System.out.println("Printing rail network flows for year: "+years[i]);
//				//creating new writer instance to ensure old values are not retained
//				writer = new OutputWriter();
//				writer.printEdgeOutput(RailEdgeOutputFilePath+"_"+years[i], freightNetwork,
//						freightNetwork.getRailNetworkGraph(), years[i], 2, false);
				freightNetwork.resetRailNetworkGraph();	

//				=============================road_HDV assignment		
				java.lang.System.out.println("Assigning flow on the road HDV network for year "+years[i]);	
				Assignment_Update roadHDVAssignment = new Assignment_Update (freightNetwork, road_HDVChoiceNetwork,1, OD,false,false);
							
				endTime=System.currentTimeMillis();
				processTime= endTime-startTime;
				System.out.println("Assignment for road mode is done, time: "+processTime+" ms");
				
//				=============================printing tkm for each mode
				System.out.println("printing TKM road HDV");
				tkmRoad_HDV[i]=writer.printTKMOutput(freightNetwork, years[i],1, TKMPath);	
				freightNetwork.resetRoadNetworkGraph();
				
//				=============================road_MDV assignment		
				java.lang.System.out.println("Assigning flow on the road MDV network for year "+years[i]);	
				Assignment_Update roadMDVAssignment = new Assignment_Update (freightNetwork, road_MDVChoiceNetwork,3, OD,false,false);
							
				endTime=System.currentTimeMillis();
				processTime= endTime-startTime;
				System.out.println("Assignment for road mode is done, time: "+processTime+" ms");
				
//				=============================printing tkm for each mode
				System.out.println("printing TKM road MDV");
				tkmRoad_MDV[i]=writer.printTKMOutput(freightNetwork, years[i],3, TKMPath);	
				freightNetwork.resetRoadNetworkGraph();
				
//				=============================road_LDV assignment		
				java.lang.System.out.println("Assigning flow on the road LDV network for year "+years[i]);	
				Assignment_Update roadLDVAssignment = new Assignment_Update (freightNetwork, road_LDVChoiceNetwork,4, OD,false,false);
							
				endTime=System.currentTimeMillis();
				processTime= endTime-startTime;
				System.out.println("Assignment for road mode is done, time: "+processTime+" ms");
				
//				=============================printing tkm for each mode
				System.out.println("printing TKM road LDV");
				tkmRoad_LDV[i]=writer.printTKMOutput(freightNetwork, years[i],4, TKMPath);	
				freightNetwork.resetRoadNetworkGraph();

//				=============================sea assignment
//				java.lang.System.out.println("Assigning flow on the maritime network for year "+years[i]);						
//				Assignment_Update seaAssignment = new Assignment_Update(freightNetwork, 3,OD,false,false);	

//				endTime=System.currentTimeMillis();
//				processTime= endTime-startTime;
//				System.out.println("Assignment for sea mode is done, time: "+processTime+" ms");
				
//				writer.printMaritimeTKMOutput(freightNetwork, years[i], maritimeTKMPath);

//				writePortThroughput(freightNetwork);				
//				System.out.println("Printing sea network flows for year: "+years[i]);
//				writer.printEdgeOutput(MaritimeEdgeOutputFilePath+"_"+years[i], 
//						freightNetwork,
//						freightNetwork.getSeaNetworkGraph(), years[i],3,false);
//				seaPool.shutdown();				
//				freightNetwork.resetSeaNetworkGraph();
				
				//printing the updated cost matrices for all modes	
//				writer.printBaseCostModel(costByModesPath, freightNetwork,OD , years[i]);		
			}
			//visualizing rail network assignment results
//			drawMap(freightNetwork,1);
		}
		
		//print all the relevant indicators in an easy to translate to excel way
		System.out.println("======================Printing Weight and TKM===================");
//		System.out.println("RAIL volume");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(weightRail[index]+",");
		}
		System.out.println();
//		System.out.println("ROAD volume");
//		System.out.println("LDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(weightRoad_LDV[index]+",");
		}
		System.out.println();
		
//		System.out.println("MDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(weightRoad_MDV[index]+",");
		}
		System.out.println();
		
//		System.out.println("HDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(weightRoad_HDV[index]+",");
		}
		System.out.println();
		System.out.println("================================TKM==============================");	
//		System.out.println("RAIL TKM");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(tkmRail[index]+",");
		}
		System.out.println();
//		System.out.println("ROAD TKM");
//		System.out.println("LDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(tkmRoad_LDV[index]+",");	
		}	
		System.out.println();
		
//		System.out.println("MDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(tkmRoad_MDV[index]+",");	
		}	
		System.out.println();
		
//		System.out.println("HDV:");
		for(int index=0; index<years.length; index++)
		{
			System.out.print(tkmRoad_HDV[index]+",");	
		}
		System.out.println();
		
		//thresholds for freight flow visualization, more thresholds mean more maps!
		double[] thresholds={1E6,5E6};
		//drawing multiple maps
		drawMap(freightNetwork,1, thresholds);
	}

	private void writePortThroughput(MultiModalNetwork freightNetwork) throws FileNotFoundException, IOException {
		double modelThroughput=0;
		//		double modelTranshipment=0;
		double observedThroughput=0;
		//		double observedTranshipment=0;
		double sumDifThroughput=0;
		double diffThroughput=0;
		//		double sumDifTranshipment=0;

		/* Open the file */
		FileOutputStream fos = new FileOutputStream(portOutputpath+".csv", false);
		OutputStreamWriter osw = new OutputStreamWriter(fos);
		BufferedWriter bw = new BufferedWriter(osw);
		bw.write("id,port,modelThroughput,observedThroughput,difference,handling cost,sea area,country ");
		bw.newLine();

		//kpi starts from 1 as the port ID starts from 1
		//		System.out.println("port, modelThroughput, observedThroughput");
		for (int index=1; index<=freightNetwork.getRealPorts().size(); index++) {

			PortNode portNode = freightNetwork.getRealPorts().get(index);
			//			System.out.println("index, port:"+index+","+portNode.getLabel());

			modelThroughput=portNode.getThroughput();
			observedThroughput=portNode.getObservedThroughput();
			diffThroughput =Math.abs(modelThroughput-observedThroughput);
			sumDifThroughput=sumDifThroughput+Math.abs(modelThroughput-observedThroughput);

			//			sumDifTranshipment=sumDifTranshipment+Math.abs(modelTranshipment-observedTranshipment);
			//			System.out.println(portNode.getName()+"," 
			//					+modelThroughput+","
			//					+observedThroughput+","+diffThroughput+","
			//					+portNode.getHandlingCosts()+","
			//					+portNode.getSeaArea()+","
			//					+portNode.getCountry()
			//					);

			bw.write(index+","+portNode.getName()+"," 
					+modelThroughput+","
					+observedThroughput+","+diffThroughput+","
					+portNode.getHandlingCosts()+","
					+portNode.getSeaArea());
			bw.newLine();
		}
		System.out.println("sumDifThroughput: "+sumDifThroughput);
		bw.close();
		fos.close();
	}
	
	private static void drawMap(MultiModalNetworkCostNoSQL network,int mode) {
		//additional graphic feature, here the visualization frame is instantiated
		JFMVisualizationMap vis = new JFMVisualizationMap(network,mode);
	}
	
	private static void drawMap(MultiModalNetworkCostNoSQL network,int mode, double[] thresholds) {		
		//drawing multiple map based on the thresholds specified
		for (int i = 0; i < thresholds.length; i++) {			
			//additional graphic feature, here the visualization frame is instantiated
			JFMVisualizationMap vis = new JFMVisualizationMap(network,mode, thresholds[i]);	
		}	
	}
}
