package jfm.output;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import jfm.commodities.Commodity;
import jfm.main.JavaFreightModel;
import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.network.RoadEdge;
import jfm.scenario.ODdata;

public class OutputWriter {
		
	private static final String weight = null;
	private ODdata baseOD;
	private ArrayList<Commodity> commodities;
	private HashMap<Integer, Centroid> centroids;
	private int modes;
	private long endTime;
	private long processTime;
	private boolean printHeaderValueWeight=true;
	private boolean printHeaderBaseCost=true;
	private boolean printHeaderEdgeFile=true;
	private boolean printHeaderODFile=true;
	
	private boolean printExtensiveHinterland=false;
	
	public OutputWriter()
	{

	}
	
	public void printValueWeightOutput (String path, MultiModalNetwork freightNetwork, int year)
	{
		System.out.println("Printing value to weight output for: "+year);
		this.centroids =freightNetwork.getCentroids();
		this.commodities =freightNetwork.getCommodities();
		this.baseOD = freightNetwork.getBaseOD();
		this.modes =freightNetwork.getModes();
		System.out.println("print header?: "+printHeaderValueWeight);
		try
		{	
			/* Open the file */
			FileOutputStream fos = new FileOutputStream(path+".csv", true);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			BufferedWriter bw = new BufferedWriter(osw);

			//print file header if this function is called the first time
			if (printHeaderValueWeight==true)
			{
				bw.write("FromC,ToC,Commodity,year,Value_Air,Value_Rail,Value_Road,Value_Sea,Value_Sea2,Value_Waterways,"
						+"Weight_Air,Weight_Rail,Weight_Road,Weight_Sea,Weight_Sea2,Weight_Waterways,"
						+"Tkm_Air,Tkm_Rail,Tkm_Road,Tkm_Sea,Tkm_sea2,Tkm_waterways");
				bw.newLine();
				printHeaderValueWeight=false;	
			}

			for(int origin=1;origin<=centroids.size();origin++)
			{
				//for each destination
				for(int destination=1;destination<=centroids.size();destination++)
				{
					//for each commodity
					for(int commodity=0;commodity<commodities.size();commodity++)
					{
						
						bw.write(origin+","+destination+","+commodity+","+year+",");
						for(int mode1=0;mode1<modes;mode1++)
						{
							//printing the values by modes in ($)
							bw.write(baseOD.getValuesByModes(origin,destination, mode1)[commodity]+",");
						}

						for(int mode2=0;mode2<modes;mode2++)
						{
							//printing the tonnage by mode
							bw.write(baseOD.getWeightODByModes(origin, destination, mode2)[commodity]+",");
						}

						//printing the tkm based on precalculated distance
						for(int mode3=0;mode3<modes;mode3++)
						{
							//for last column
							if(mode3==modes-1)
							{
								bw.write(baseOD.getWeightODByModes(origin, destination, mode3)[commodity]*baseOD.getODDistance(origin, destination, mode3)+"");
							}
							else
								//printing the tkm by mode for each centroid pairs
								bw.write(baseOD.getWeightODByModes(origin, destination, mode3)[commodity]*baseOD.getODDistance(origin, destination, mode3)+",");
						}
						bw.newLine();
					}
				}
			}	
			/* Close the file */
			bw.close();
			fos.close();
			endTime=System.currentTimeMillis();
			processTime= endTime-JavaFreightModel.startTime;
			System.out.println("Output file for year "+year+" is printed, time: "+processTime+" ms");
		} catch (IOException e)
		{
			e.printStackTrace();
		}    
	}
	
	public void printBaseCostModel (String path, MultiModalNetwork freightNetwork, ODdata OD, int year)
	{
		System.out.println("Printing base case cost for: "+year);
		this.centroids =freightNetwork.getCentroids();
		this.baseOD = OD;
		this.modes =freightNetwork.getModes();
		System.out.println("print header?: "+printHeaderBaseCost);
		try
		{	
			/* Open the file */
			FileOutputStream fos = new FileOutputStream(path+".csv", false);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			BufferedWriter bw = new BufferedWriter(osw);
			
			//print file header if this function is called the first time
			if (printHeaderBaseCost==true)
			{
				bw.write("FromC,ToC,From,To,Time,Distance,"
						+"Cost,Mode,fromProvince,toProvince");
				bw.newLine();
				printHeaderBaseCost=false;	
			}
			
			for(int origin=1;origin<=centroids.size();origin++)
			{
				//for each destination
				for(int destination=1;destination<=centroids.size();destination++)
				{
					
					if(centroids.get(origin).getId()==centroids.get(destination).getId())
						continue;
					for(int mode=0;mode<modes;mode++)
					{	
						String from =centroids.get(origin).getName();
						String to =centroids.get(destination).getName();
						
//						double distance = baseOD.getODDistance(origin, destination, mode);
//						double cost = baseOD.getODCost(origin, destination, mode);
//						double continent = baseOD.getContig(origin, destination);
//						double lang = baseOD.getLanguage(origin, destination);
//						double rta = baseOD.getRta(origin, destination);
						//exception for kepulauan seribu
						if(origin==1)
						{
							double time = baseOD.getODTime(origin, destination, mode);
							bw.write(origin+","+destination+","+from+","+to+","
									+time+","
									+baseOD.getODDistance(origin, destination, mode)+","
									+baseOD.getODCost(origin, destination, mode)+","+mode+","
									+centroids.get(origin).getRegion()+","+centroids.get(destination).getRegion());
							bw.newLine();
						}
						else
						{
						bw.write(origin+","+destination+","+from+","+to+","
								+baseOD.getODTime(origin, destination, mode)+","
								+baseOD.getODDistance(origin, destination, mode)+","
								+baseOD.getODCost(origin, destination, mode)+","+mode+","
								+centroids.get(origin).getRegion()+","+centroids.get(destination).getRegion());
						bw.newLine();
						}
					}
				}
			}	
			/* Close the file */
			bw.close();
			fos.close();
			endTime=System.currentTimeMillis();
			processTime= endTime-JavaFreightModel.startTime;
			System.out.println("Cost file for year "+year+" is printed, time: "+processTime+" ms");
		} catch (IOException e)
		{
			e.printStackTrace();
		}    
	}

	public void printODFLowsTransition (String path, HashMap<String,HashMap<String,HashMap<String,double[]>>> ODflows, String mode, int year)
	{
		System.out.println("printing OD flows information");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(path+".csv", true);

			OutputStreamWriter osw = new OutputStreamWriter(fos);
			BufferedWriter bw = new BufferedWriter(osw);

			//print file header if this function is called the first time (for multiple year analysis)
			if (printHeaderEdgeFile==true)
			{
				bw.write ("ISO_O,ISO_D,ISO_T,main mode,weight,tkm,Type");
				bw.newLine();
				printHeaderEdgeFile=false;
			}
			
			for (String Origin : ODflows.keySet()) 
			{
				for (String Destination : ODflows.get(Origin).keySet()) 
				{
					for (String Transit : ODflows.get(Origin).get(Destination).keySet())
					{
						double weight=ODflows.get(Origin).get(Destination).get(Transit)[0];
						double km=ODflows.get(Origin).get(Destination).get(Transit)[1];
						
						if(mode=="Road")
						{
							bw.write(Origin+","+Destination+","+Transit+","+mode+","+weight+","+weight*km+", pure road");
							bw.newLine();
						}
						else
						{
							bw.write(Origin+","+Destination+","+Transit+","+mode+","+weight+","+weight*km+", intermodal");
							bw.newLine();
						}
					}
				}
			}			
			bw.close();
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		

	public double printWeightOutput(MultiModalNetworkCostNoSQL freightNetwork, int mode, int year)
	{
		this.centroids =freightNetwork.getCentroids();
		int commoditySize=1;
		this.baseOD = freightNetwork.getBaseOD();
	
		double weight = 0;
		double value = 0;
		for(int origin=1;origin<=centroids.size();origin++)
		{
			//for each destination
			for(int destination=1;destination<=centroids.size();destination++)
			{
				//for each commodity
				for(int commodity=0;commodity<commoditySize;commodity++)
				{
						//printing the tonnage by mode
						weight= weight+baseOD.getWeightODByModes(origin, destination, mode)[commodity];
				}
			}
		}
//		System.out.println("mode and total weight: "+mode+","+weight);
		System.out.println(+mode+","+weight);
		return weight;
	}
	
	public double printTKMOutput(MultiModalNetworkCostNoSQL freightNetwork, int year, int mode, String path)
	{
		Collection<AbstractEdge> edges = null;
		if (mode==0)
			edges=freightNetwork.getRailNetworkGraph().getEdges();
		if (mode==1||mode==3||mode==4)
			edges=freightNetwork.getRoadNetworkGraph().getEdges();
		if (mode==2)
			edges=freightNetwork.getSeaNetworkGraph().getEdges();

		double tkm=0;
		
		//printing all the edges
		for (AbstractEdge edge : edges) 
		{
			if(mode==0)
			{
				if(edge.getMode().equalsIgnoreCase("Railway"))
				{
//					double flow=edge.getAssignedflow();
//					double distance =edge.getLength();		
//					tkm = tkm+(flow*distance);					
					tkm =tkm+edge.getTkm_commodity()[0];
				}
			}
			else
			{
				double flow=edge.getAssignedflow();
				double distance =edge.getLength();		
				tkm = tkm+(flow*distance);
			}
		}
		System.out.println(+mode+","+tkm);
		return tkm;
	}
	
	public void printEdgeOutput (String path, MultiModalNetworkCostNoSQL freightNetwork,	
			UndirectedSparseGraph<AbstractNode, AbstractEdge> network,int year, int modeID, 
			boolean printExtensiveHinterland)
	{	
		System.out.println("print extensive hinterland?: "+printExtensiveHinterland);
		this.centroids =freightNetwork.getCentroids();
		this.commodities =freightNetwork.getCommodities();
		this.baseOD = freightNetwork.getBaseOD();
		this.modes =freightNetwork.getModes();
		this.printExtensiveHinterland=printExtensiveHinterland;

		/* Open the file */
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(path+".csv", false);

			OutputStreamWriter osw = new OutputStreamWriter(fos);
			BufferedWriter bw = new BufferedWriter(osw);

			//print file header if this function is called the first time (for multiple year analysis)
			if (printHeaderEdgeFile==true)
			{
//				bw.write("EdgeId,year,mode,distance,Load_bulk,Load_container,Load_General_Cargo,Load_tanker,Load_RoRo,"
//						+ "Weight_bulk,Weight_container,Weight_General_Cargo,Weight_tanker,Weight_RoRo,"
//						+ "Domestic,Domestic_weight,Ttime,Ttime_ref"+tkm_edges+vkm_edges);
				String tkm_edges="";
				String vkm_edges=""; 
				for(int u=0;u<25;u++)
				{
					if(u==0)
					{
						tkm_edges=",tkm"+(u+1);
						vkm_edges=",vkm"+(u+1);
					}
					else
					{
						tkm_edges+=",tkm"+(u+1);
						vkm_edges+=",vkm"+(u+1);
					}
				}
				
				bw.write( "EdgeId,year,"
						+ "fromNodeID,toNodeID,mode,"
						+ "Geometry_XLO,Geometry_YLO,"
						+ "Geometry_XHI,Geometry_YHI,"
						+ "distance,Weight,Ttime,"
						+ "Weight_bulk,Weight_container,Weight_General_Cargo,Weight_tanker,Weight_RoRo,"
						+ "tkm_domesticRoad,tkm_domesticRail"
						+ tkm_edges+vkm_edges+",ISO,Region");

				bw.newLine();
				printHeaderEdgeFile=false;
			}
			
			Collection<AbstractEdge> roadEdges= network.getEdges();
			//printing all the edges
			for (AbstractEdge edge : roadEdges) {
				int edgeID=edge.getRindex();
				//TODO temporarily focusing on CO2 emissions
				double flow=edge.getCO2();

				//getting the weight of different cargo types
				double[] weightByCargo=edge.getWeightCategories();	
				double weight_container=weightByCargo[0];
				double weight_bulk= weightByCargo[1];
				double weight_tanker=weightByCargo[2];
				double weight_General_Cargo=weightByCargo[3];
				double weight_RoRo=weightByCargo[4];

				//				double weight_domesticRoad =edge.getDomestic_RoadWeight();
				double tkm_domesticRoad =edge.getDomestic_RoadTKM();

				//				double weight_domesticRail =edge.getDomestic_RailWeight();
				double tkm_domesticRail =edge.getDomestic_RailTKM();

				//getting tkm for all commodities
				double[] tkm_commodity = edge.getTkm_commodity();
				double[] vkm_commodity = edge.getVkm_commodity();
				String tkm_edges=""; 
				String vkm_edges="";
				for(int u=0;u<25;u++)
				{
					if(u==0)
					{
						tkm_edges=""+tkm_commodity[u];
						vkm_edges=""+vkm_commodity[u];
					}
					else
					{
						tkm_edges+=","+tkm_commodity[u];
						vkm_edges+=","+vkm_commodity[u];
					}
				}

				String mode= edge.getMode();
				double distance =edge.getLength();
				double travelTime=0;
				int fromNodeID = edge.getEndpoints().getFirst().getId();
				int toNodeID= edge.getEndpoints().getSecond().getId();
				double firstLat= edge.getEndpoints().getFirst().getLat();
				double firstLon = edge.getEndpoints().getFirst().getLon();

				double secondLat = edge.getEndpoints().getSecond().getLat();
				double secondLon = edge.getEndpoints().getSecond().getLon();
				
				String ISO = freightNetwork.getEdges_country().get(edgeID);
				String Region = freightNetwork.getEdges_region().get(edgeID);

				bw.write(edgeID+","+year+","+fromNodeID+","+toNodeID+","+mode
						+","+firstLat+","+firstLon+","+secondLat+","+secondLon
						+","+distance+","+flow+","+travelTime
						+","+weight_bulk+","+weight_container+","+weight_General_Cargo+","+weight_tanker+","+weight_RoRo
						+","+tkm_domesticRoad+","+tkm_domesticRail
						+","+tkm_edges+","+vkm_edges+","+ISO+","+Region);
				bw.newLine();				
			}
			
			//writing output for hinterland if mode is sea transport
//			if(modeID==3)
//			{
//				if(printExtensiveHinterland)
//				{
//					//do nothing as the extensive hinterland is already part of the network object
//				}
//				//if false
//				else
//				{
//					System.out.println("printing simplified port hinterland flows");
//					HashMap<Integer, HinterlandEdge> portHinterlandEdges=freightNetwork.getPortHinterlandLinks();
//					for (HinterlandEdge hEdge : portHinterlandEdges.values()) 
//					{
//						int edgeID=hEdge.getId();
//						double flow=hEdge.getAssignedflow();
//						String mode= hEdge.getMode();
//						double distance =hEdge.getDistance();
//						double travelTime=hEdge.getTime();
//
//						int fromNodeID = hEdge.getEndpoints().getFirst().getId();
//						int toNodeID= hEdge.getEndpoints().getSecond().getId();
//						double firstLat= hEdge.getEndpoints().getFirst().getLat();
//						double firstLon = hEdge.getEndpoints().getFirst().getLon();
//
//						double secondLat = hEdge.getEndpoints().getSecond().getLat();
//						double secondLon = hEdge.getEndpoints().getSecond().getLon();
//						double tkm_domesticRoad =hEdge.getRoadTKM();
//						double tkm_domesticRail =hEdge.getRailTKM();
//						String ISO=hEdge.getISO();
//
//						double[] tkm_commodity = new double[25];
//						double[] vkm_commodity = new double[25];
//						String tkm_edges=""; 
//						String vkm_edges="";
//						for(int u=0;u<25;u++)
//						{
//							if(u==0)
//							{
//								tkm_edges=""+tkm_commodity[u];
//								vkm_edges=""+vkm_commodity[u];
//							}
//							else
//							{
//								tkm_edges+=","+tkm_commodity[u];
//								vkm_edges+=","+vkm_commodity[u];
//							}
//						}
//						bw.write(edgeID+","+year+","+fromNodeID+","+toNodeID+","+mode
//								+","+firstLat+","+firstLon+","+secondLat+","+secondLon
//								+","+distance+","+flow+","+travelTime
//								+","+0+","+0+","+0+","+0+","+0
//								+","+tkm_domesticRoad+","+tkm_domesticRail
//								+","+tkm_edges+","+vkm_edges+","+ISO);
//						bw.newLine();
//					}
//				}
//			}
				bw.close();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
