package jfm.network;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections15.Transformer;
import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Node;
import org.geotools.graph.structure.basic.BasicGraph;
import org.locationtech.jts.geom.Point;
import au.com.bytecode.opencsv.CSVReader;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;
import jfm.commodities.Commodity;
import jfm.models.ModeShare;
import jfm.models.ModeShareCost;
import jfm.models.ValueToWeight;
import jfm.models.ValueToWeightLogSum;
import jfm.scenario.ODdata;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;


public class MultiModalNetworkCostNoSQL extends MultiModalNetwork  {
	private ODdata baseOD;
	private ArrayList<Commodity> commodities;
		
	//2 versions of mode share model: original and cost-based
	private ModeShareCost modeShareCost;
	
	//all edges in the database
	private HashMap<Integer, AbstractEdge> edges;	
	//all nodes in the database
	private HashMap<Integer, AbstractNode> nodes;
	//all rail nodes
	private HashMap<Integer, AbstractNode> railNodes;
	//all road nodes
	private HashMap<Integer, AbstractNode> roadNodes;
	
	//all ports in the database
	private HashMap<Integer, PortNode> ports;
	private HashMap<Integer, PortNode> dryPorts;
	private HashMap<Integer, PortNode> stations;
	
	private HashMap<Integer,MaritimeNode> maritimeNodes;
	private HashMap<Integer, Centroid> centroids;
	private HashMap<String, Centroid> centroidsByName;
	private HashMap<Integer, HinterlandEdge> portHinterlandLinks;
	private HashMap<Integer, RoadEdge> centroidStationLinks;
	
	//directory of port cost
	private String mode_share_dir ="input data/mode_share_model.csv";
	private String centroid_dir ="input data/centroids.csv";
	private String nodes_dir ="input data/Omnitrans_nodes_all.csv";
	private String port_dir="input data/ports.csv";
	private String centroid_to_station_dir="input data/centroid_to_station_filtered.csv";
	private String multiplier_dir="input data/multiplier_OD.csv";	
	
	private String OD_matrix_dir ="input data/OD_matrix_optimistic_adjusted.csv";
	private String baseOD_dir ="input data/baseOD.csv";
	
	private String shapefiledir= "input data/GIS java transport network/"
			+ "QGIS projects/Combined_multi_modal_network_fixed.shp";
	
//	private String shapefiledir= "input data/Combined_multimodal_network.shp";
	
	private String maritimeNode_dir="input data/maritime_node.csv";
	private String maritimeLinks_dir="input data/maritime_link.csv";

	private String carbon_intensity_dir = "input data/BAUCarbonIntensity.csv";
	
	private HashMap<Integer,String> edges_region = new HashMap<Integer,String>();
	
	//??evolution of price, carbon intensity
	private double[][] carbonIntensity = new double[25][9];

	private UndirectedSparseGraph<AbstractNode, AbstractEdge> roadNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> roadShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> railNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> railShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> seaNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> seaShortestPath;
	
	//this needs to take an argument from the main 
	private boolean update_capacity=true;
	private int timeHorizon = 5;
	private int modes = 5;
	private int commoditySize = 1;
	private long endTime;
	private long processTime;
	private int commodityIndex = 0; //denoting the first and the only commodity type

	public MultiModalNetworkCostNoSQL() throws SQLException
	{
		this.seaNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.roadNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.railNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.portHinterlandLinks= new HashMap<Integer,HinterlandEdge>();
		this.centroidStationLinks= new HashMap<Integer,RoadEdge>();	
	}//end of constructor

	public void buildMultimodalNetwork(long startTime) {
	
		//parsing value mode share model parameters
		System.out.println("Parsing mode share model parameters");
		//cost-based mode share
		buildModeChoice(mode_share_dir);
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("cost-based mode share model is parsed, time: "+processTime+" ms");	
		
		//parsing nodes, except rail nodes since we don't have their ids
		System.out.println("Parsing nodes");
		parseNodes();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("nodes are parsed, time: "+processTime+" ms");	
				
		//parsing centroid
		System.out.println("Parsing centroids");
		centroids = new HashMap<Integer,Centroid>();
		centroidsByName = new HashMap<String,Centroid>();
		//assigning values of the centroid attributes from the database to the objects 
		parseCentroids();	
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("centroids are parsed, time: "+processTime+" ms");
		
		//parsing port nodes
		System.out.println("Parsing ports, dry ports and station nodes");		
		parsePortFile(port_dir);
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("ports, dry ports and station nodes are parsed, time:" +processTime+" ms");
		
		//parsing the shapefile transport network
		//AFTER All the nodes file
		System.out.println("Parsing transport network");	
		ShapefileReader shapefile;
		try {
			shapefile = new ShapefileReader(shapefiledir);
			//mode of choosing the shapefile from a directory yourself
//			shapefile = new ShapefileReader(true);
			
			//generate graph from the shapefile
			BasicGraph graph = shapefile.generateGraph();
			//map to store the rail nodes
			railNodes = new HashMap<Integer,AbstractNode>();
			//map to store the road nodes
			roadNodes = new HashMap<Integer,AbstractNode>();
			//multimap for storing graph relationships for rail network
			Multimap<Integer, Object[]> railNetwork = ArrayListMultimap.create();
			//HashMap to store the edge attributes
//			HashMap<Integer, Object[]> railAttributes= new HashMap<Integer, Object[]>();
			
			//1.loop through the edges of the graph from geotools
			//2.create road edge based on the attributes
			//3. add edges into the freight network graph
			Collection<Edge> edges=graph.getEdges();	
			int i=1;
			for (Edge edge : edges) 
			{
				SimpleFeature link = ((SimpleFeature)edge.getObject());
				int edgeID= Integer.valueOf((String) link.getAttribute("ID"));			
				Double distance = (Double) link.getAttribute("LENGTH");
				Double time = (Double) link.getAttribute("TIME");
				Double speed = (Double) link.getAttribute("SPEEDAB");
				String mode = (String) link.getAttribute("ROADTYPEAB");
				String active = (String) link.getAttribute("AKTIF");
				String lintas = (String) link.getAttribute("LINTASID");
				int fromNodeID=Integer.valueOf((String) link.getAttribute("ANODE"));		
				int toNodeID=Integer.valueOf((String) link.getAttribute("BNODE"));				
				
				Point fromNode = ((Point)edge.getNodeA().getObject());
				Point toNode = ((Point)edge.getNodeB().getObject());
				Double fromLat=fromNode.getY();
				Double fromLon=fromNode.getX();
				Double toLat=toNode.getY();
				Double toLon=toNode.getX();		
				
				//to make rail id to be unique
				int nodeAID=edge.getNodeA().getID()+20000;
				int nodeBID=edge.getNodeB().getID()+20000;
				
//				System.out.println("node A id, node B id: "+nodeAID+","+nodeBID);	
				//building the edges from the existing nodes database
				AbstractNode NodeA= nodes.get(fromNodeID);
				AbstractNode NodeB= nodes.get(toNodeID);
			  	
				//usually only rail nodes that are null
				//this way of creating an id makes both node A or B not connected to other nodes
				//not going to be added anywhere!!
				if(NodeA==null)
			  	{
//			  		System.out.println("node A is null, edge "+i+", ID: "+edgeID+", mode: "+mode+","+" node id "+fromNodeID);
			  		NodeA= new AbstractNode(fromLat,fromLon,"rail node",nodeAID);
			  		if(NodeB==null)
				  	{
//				  		System.out.println("node B is null, edge "+i+", ID: "+edgeID+", mode: "+mode+","+" node id "+toNodeID);
				  		NodeB= new AbstractNode(toLat,toLon,"rail node",nodeBID);
				  	}
//			  		System.out.println("created edge "+i+", ID: "+edgeID+", mode: "+mode+","+fromNodeID +","+toNodeID+" speed: "+speed);
			  	}	  	
				
//				System.out.println("created edge "+i+", ID: "+edgeID+", mode: "+mode+","+fromNodeID +","+toNodeID);
				//is node A or B going to be a shared node? i don't think so
			  	Pair<AbstractNode> endpoints= new Pair<AbstractNode>(NodeA,NodeB);		  
			  	//road edge construction
				RoadEdge road = new RoadEdge(endpoints, edgeID, mode, speed, time);
				
				//set the penalty and then add it into the respective network
				if (mode.equalsIgnoreCase("Road 2 lanes"))
				{	
					this.roadNodes.put(fromNodeID, NodeA);
					this.roadNodes.put(toNodeID, NodeB);
					this.roadNetwork.addEdge(road, endpoints);
					
//					this.railNetwork.addEdge(road, endpoints);
//					System.out.println("Road edge "+i+", ID: "+edgeID+", mode: "+mode+", A Node and B Node: "+fromNodeID+" to "+toNodeID+": "+fromLat +","+fromLon +","+toLat+","+toLon);			
				}
				
				else if (mode.equalsIgnoreCase("Connector"))
				{
					//this should take the centroid as the origin and destination of the edge
					//one way of doing this is by using node hashmap and node id to ensure the first or the last node is a centroid node
					this.roadNetwork.addEdge(road, endpoints);
//					System.out.println("Connector edge "+i+", ID: "+edgeID+", mode: "+mode+", from Node and to Node: "+fromNodeID+" to "+toNodeID+": "+fromLat +","+fromLon+","+toLat+","+toLon);
				}
		
				else if (mode.equalsIgnoreCase("Railway"))
				{
//					this.railNetwork.addEdge(road, endpoints);
					//developing graph database system for rail network
					NodeA= new AbstractNode(fromLat,fromLon,"rail node",nodeAID);
					NodeB= new AbstractNode(toLat,toLon,"rail node",nodeBID);
					//making a consistent list of rail nodes
					railNodes.put(nodeAID, NodeA);
					railNodes.put(nodeBID, NodeB);

					//transferring the edge attributes to a new database for rail
					Object [] att= new Object[5];
					att[0]=speed;
					att[1]=time;
					att[2]=lintas;
					att[3]=nodeBID;
					att[4]=edgeID;
		
					railNetwork.put(nodeAID,att);
					
//					System.out.println("Railway edge "+edgeID+": fromID-toID: "+nodeAID+","+nodeBID+","+" speed, time, lintas: "+speed+","+time+","+lintas);
					//adding railway network but this is purely for visualization purpose
//					roadNetwork.addEdge(road, endpoints);
				}
				//handling is between road network and rail station
//				else if (mode.equalsIgnoreCase("Handling"))
//				{
//					//this should take the station or port as the origin and destination of the edge
//					this.roadNetwork.addEdge(road, endpoints);
////					System.out.println("edge "+i+", ID: "+edgeID+", mode: "+mode+", from Node and to Node: "+fromNodeID+" to "+toNodeID+": "+fromLat +","+fromLon +","+toLat+","+toLon);
//				}
				
				//adding maritime link
				//TODO: we will deal with the construction of port-maritime network and centroid-port later.
				else if (mode.equalsIgnoreCase("Maritime Link"))
				{
					this.seaNetwork.addEdge(road, endpoints);
//					System.out.println("edge "+i+", ID: "+edgeID+", mode: "+mode+", from Node and to Node: "+fromNodeID+" to "+toNodeID+": "+fromLat +","+fromLon +","+toLat+","+toLon);
				}
				i++;
			}
			
			//building rail network
			//FIXME: this is where you set train speed
			Set<Integer> fromNodes=railNetwork.keySet();
			for (Integer fromNode: fromNodes)
			{
				Collection<Object[]> nodeDestination = railNetwork.get(fromNode);
				for (Object[] object: nodeDestination)
				{
					int edgeID =(Integer)object[4];
					int toNode = (Integer)object[3];
					//from the database
					double speed = (Double) object[0];
					//own modification
//					double speed = 60.0;			
					double time =(Double) object[1];
					String lintas = (String) object[2];
					
					AbstractNode railFromNode = railNodes.get(fromNode);
					AbstractNode railToNode = railNodes.get(toNode);
					Pair<AbstractNode> endpoints= new Pair<AbstractNode>(railFromNode,railToNode);
					
//					System.out.println("edgeID, from, to, speed, time, lintas "+edgeID+","+fromNode+","+toNode+","+speed+","+time+","+lintas);
					RoadEdge road = new RoadEdge(endpoints, edgeID, "Railway",speed, time);
					
//					System.out.println("edgeID, from, to, speed, time, lintas "+edgeID+","+fromNode+","+toNode+","+speed+","+time+","+lintas);
					this.railNetwork.addEdge(road, endpoints);
				}
			}		
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		    	
		constructShortestPath();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("roads are parsed, time: "+processTime+" ms");	

		//TODO: not yet implemented: parsing maritime nodes
//		System.out.println("Parsing simplified maritime network");
//		parsingSeaNetwork(startTime);
			
		//creating station.dry port to rail network connection
		System.out.println("creating dry port/ station-rail connections");
		createStationToRailLinks();
		System.out.println("dry port-rail connections are created");	
		
		System.out.println("creating road to station connections");
		createRoadtoStationConnection();
		System.out.println("road to station connections are created");
		
		//parsing centroid to station connection
		System.out.println("Parsing centroid-station connections");
		parseCentroidtoStationConnection();
		System.out.println("centroid-station connections are parsed");
		
		System.out.println("Parsing base case OD characteristics");
//		//here the centroid array size is +1 to allow arrays to be numbered based on the IDs
		baseOD = new ODdata ("baseline scenario",centroids.size()+1,modes,commoditySize,timeHorizon);
		System.out.println("centroid size, mode, commodity, time horizon: "+centroids.size()+","+modes+","
		+commoditySize+","+timeHorizon);	
		
		System.out.println("Parsing weight OD data");
		parseODData();
		System.out.println("OD Flows are parsed");
				
		//multiplier for incorporating volume of international trade
		double[] multiplier_o =new double[centroids.size()+1];
		double[] multiplier_d =new double[centroids.size()+1];
		System.out.println("Parsing multiplier for OD data");
		try {		
			CSVReader reader = new CSVReader(new FileReader(multiplier_dir), ',', '\'',1); 
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null) 
			{
				int ID=Integer.valueOf(nextLine[0]);
				multiplier_o[ID] = Double.valueOf(nextLine[2]);
				multiplier_d[ID] = Double.valueOf(nextLine[3]);
//				System.out.println("ID,multiplier_o,multiplier_d: "+ID+","+multiplier_o[ID]+","+multiplier_d[ID]);
			}
			reader.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//applying the multiplier
		for (int i=0; i<timeHorizon; i++)
		{
			//			modify OD matrix to include international flows step 1:origin modification
			double totalWeight=0;
			for (int origin=1; origin<centroids.size()+1; origin++)
			{
				for (int destination=1; destination<centroids.size()+1; destination++)
				{
					double weight=baseOD.getWeightOD_year(origin, destination, commodityIndex, i)*multiplier_o[origin];
					baseOD.setWeightOD_year(origin, destination,commodityIndex, weight, i);
				}
			}
		
			//modify OD matrix to include international flows step 2:destination modification
			for (int destination=1; destination<centroids.size()+1; destination++)
			{
				for (int origin=1; origin<centroids.size()+1; origin++)
				{
					double weight=baseOD.getWeightOD_year(origin, destination, commodityIndex, i)*multiplier_d[destination];
					baseOD.setWeightOD_year(origin, destination,commodityIndex, weight, i);
					totalWeight+=weight;
				}
			}
//			System.out.println("total weight for year "+i+" is:"+totalWeight);
		}
		
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("network components are parsed, time: "+processTime+" ms");
	}

	private void parseODData() {
		try {		
			CSVReader reader = new CSVReader(new FileReader(OD_matrix_dir), ',', '\'',1); 
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null) 
			{		
				String from = String.valueOf(nextLine[5]);
				String to = String.valueOf(nextLine[6]);
				String prov_origin = String.valueOf(nextLine[7]);
				String prov_destination  = String.valueOf(nextLine[8]);

				//parsing the id
				//TODO: check the consistency of the node ID
				int c_i=Integer.valueOf(nextLine[0]);
				int c_j=Integer.valueOf(nextLine[1]);
				//parsing the year index
				int year=Integer.valueOf(nextLine[4]);
				int yearIndex;
				//to recognize base year 
				if (year==2011)
				{
					yearIndex=0;
				}
				else
				{
					yearIndex=Math.round((year-2010)/5);
				}
				//assign trade value  or weight values from centroid i to centroid j in year (year) for each commodity j
				double tradeFlow=Double.valueOf(nextLine[9])*1.0104; //conversion to tonnes	
				baseOD.setWeightOD_year(c_i, c_j, commodityIndex, tradeFlow, yearIndex);
//				System.out.println("origin: "+c_i+ ", destination: "+c_j+", year: "+year+", year index:"+yearIndex+
//						" volume: "+tradeFlow);	
			}
			reader.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parsingSeaNetwork(long startTime) {
		maritimeNodes= new HashMap<Integer, MaritimeNode>();
		try
		{
			CSVReader reader = new CSVReader(new FileReader(maritimeNode_dir), ',','\'', 1);
			String[] nextLine;
			while ((nextLine = reader.readNext()) != null) 
			{
				int nodeID=Integer.parseInt(nextLine[0]);
				double lat=Double.parseDouble(nextLine[1]);
				double lon=Double.parseDouble(nextLine[2]);

				MaritimeNode node = new MaritimeNode(lat,lon,"maritime node",nodeID);
				maritimeNodes.put(nodeID,node);
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			//parsing maritime links
			System.out.println("Parsing maritime links");
			CSVReader reader = new CSVReader(new FileReader(maritimeLinks_dir), ',','\'', 1);
			String[] nextLine;
			int index =1001;
			
			while ((nextLine = reader.readNext()) != null)
			{
				int fromID =Integer.valueOf(nextLine[0]);
				int toID   =Integer.valueOf(nextLine[1]);
				MaritimeNode from=maritimeNodes.get(fromID);
				MaritimeNode to = maritimeNodes.get(toID);

				Pair<AbstractNode> endpoints= new Pair<AbstractNode>(from,to);
				MaritimeEdge  edge = new MaritimeEdge(endpoints,0.0,0.0,0.0, index, "Sea");
				this.seaNetwork.addEdge(edge, endpoints);
				index++;
			}
			reader.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("maritime network is parsed, time: "+processTime+" ms");
	}

	//cost-based mode share
	private void buildModeChoice(String mode_share_dir)  {
		modeShareCost = new ModeShareCost();
		
		try {
			CSVReader reader = new CSVReader(new FileReader(mode_share_dir), ',','#', 1);
			String[] nextLine;
			while ((nextLine = reader.readNext()) != null) {			
	
				String code=String.valueOf(nextLine[1]);
				double value=Double.valueOf(nextLine[2]);
				
//				System.out.println("mode share model code, value "+code+","+value);

				//parsing mode share value, only looking for mode share by rail, road and sea	
				//asc:alternative specific coefficient for each mode of transport
				//at the moment asc is set to zero
				if(code.equalsIgnoreCase("ASC_RL"))
				{
					modeShareCost.getAsc()[0]=value;
//					System.out.println("asc rail "+value);
				}
				
				else if(code.equalsIgnoreCase("ASC_R"))
				{
					modeShareCost.getAsc()[1]=value;
//					System.out.println("asc road "+value);
				}
				
				else if(code.equalsIgnoreCase("ASC_S"))
				{
					modeShareCost.getAsc()[2]=value;
//					System.out.println("asc sea "+value);
				}
				
				//parsing contiguity coefficient for each mode of transport, only for road and rail
				else if(code.equalsIgnoreCase("TIME_RL"))
					//coefficient for road contig 0 
					modeShareCost.getTime()[0]=value;
				else if(code.equalsIgnoreCase("TIME_R"))
					//coefficient for rail contig
					modeShareCost.getTime()[1]=value;
				else if(code.equalsIgnoreCase("TIME_S"))
					modeShareCost.getTime()[2]=value;
				
				//parsing cost coefficient for each type of cargo
				else if(code.equalsIgnoreCase("COST_RL"))
				{
					//coefficient for container cargo 
					modeShareCost.getCost_Coeff()[0]=value;
				}
				else if(code.equalsIgnoreCase("COST_R"))
				{
					//coefficient for dry bulk cargo
					modeShareCost.getCost_Coeff()[1]=value;	
				}
				//coefficient for liquid bulk
				else if(code.equalsIgnoreCase("COST_S"))
					modeShareCost.getCost_Coeff()[2]=value;
				
				else if(code.equalsIgnoreCase("OR_RL"))
					//coefficient for road contig 0 
					modeShareCost.getReliability_Coeff()[0]=value;
				else if(code.equalsIgnoreCase("OR_R"))
					//coefficient for rail contig
					modeShareCost.getReliability_Coeff()[1]=value;
				else if(code.equalsIgnoreCase("OR_S"))
					modeShareCost.getReliability_Coeff()[2]=value;
				
				else if(code.equalsIgnoreCase("RS_RL"))
					//coefficient for road contig 0 
					modeShareCost.getRisk_safety_Coeff()[0]=value;
				else if(code.equalsIgnoreCase("RS_R"))
					//coefficient for rail contig
					modeShareCost.getReliability_Coeff()[1]=value;
				else if(code.equalsIgnoreCase("RS_S"))
					modeShareCost.getReliability_Coeff()[2]=value;
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	private void parseCarbonIntensity() 
	{
		try {
			CSVReader reader = new CSVReader(new FileReader(carbon_intensity_dir), ',','#', 1);
			String[] nextLine;
			while ((nextLine = reader.readNext()) != null) 
			{
				String comName = String.valueOf(nextLine[0]);
				int comID   = Integer.valueOf(nextLine[1]);
				for(int c=0; c<commodities.size(); c++)
				{
					for (int i=3; i<12; i++)
					{
						carbonIntensity[c][i-3] = Double.valueOf(nextLine[i]);
					}	
				}
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseNodes() {
		nodes = new HashMap<Integer, AbstractNode>();
		try {		
			CSVReader reader = new CSVReader(new FileReader(nodes_dir), ',', '\'',1); 
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null) 
			{
				int nodeID=Integer.valueOf(nextLine[0]);
				String name = String.valueOf(nextLine[1]);

				double lat = Double.valueOf(nextLine[3]);
				double lon = Double.valueOf(nextLine[2]);
				String type = String.valueOf(nextLine[5]);

				AbstractNode node = new AbstractNode (lat, lon, name, nodeID);
				nodes.put(nodeID, node);
//				System.out.println("nodeID, name, lat, lon, type "+nodeID+","+name+","+lat+","+lon+","+type);
			}
			reader.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void parseCentroids() {
		try {		
			CSVReader reader = new CSVReader(new FileReader(centroid_dir), ',', '\'',1); 
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null) 
			{
				int nodeID=Integer.valueOf(nextLine[0]);
				String name = String.valueOf(nextLine[1]);
				String province = String.valueOf(nextLine[6]);

				//centroidDTA, this is not not ID
				int centroidID= Integer.valueOf(nextLine[5]);
				//latitude is usually with 
				double lat = Double.valueOf(nextLine[3]);
				double lon = Double.valueOf(nextLine[2]);
				Centroid centroid = new Centroid(name,"Indonesia",province,lat, lon,nodeID);
				centroids.put(nodeID, centroid);
				
				//important: update node database with centroid node, and centroid ID
				nodes.put(nodeID, centroid);
				centroidsByName.put(name, centroid);
			}
			reader.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void parsePortFile(String port_dir) {

		ports = new HashMap<Integer,PortNode>();
		stations = new HashMap<Integer, PortNode>();
		//		dryPorts = new HashMap <Integer, PortNode>();
		try {
			CSVReader reader = new CSVReader(new FileReader(port_dir), ',','#', 1);
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null) {
				int portNodeID = Integer.valueOf(nextLine[0]);
				String portName = String.valueOf(nextLine[1]);
				double lat = Double.valueOf(nextLine[3]);
				double lon = Double.valueOf(nextLine[2]);
				String type = String.valueOf(nextLine[5]);
				double handlingCost = Double.valueOf(nextLine[6]);
				double handlingTime = Double.valueOf(nextLine[7]);
				double observedThroughput = Double.valueOf(nextLine[8]);
				double observedTranshipment = Double.valueOf(nextLine[9]);
				String cap_group =String.valueOf(nextLine[10]);

				PortNode port = new PortNode(lat, lon, portName, portNodeID, type,0, handlingCost, 
						observedThroughput,observedTranshipment);

				port.setHandlingCosts(handlingCost);
				port.setObservedThroughput(observedThroughput);

				if(cap_group.equalsIgnoreCase("BESAR A") 
						||cap_group.equalsIgnoreCase("BESAR B") 
						||cap_group.equalsIgnoreCase("BESAR C")
						||cap_group.equalsIgnoreCase("1")
						||cap_group.equalsIgnoreCase("2"))
				{
					
					if(type.equalsIgnoreCase("Port"))
					{						
						ports.put(portNodeID,port);
						//update nodeID and node database, the port is added to the network when port-maritime edge is added to the network 
						nodes.put(portNodeID,port);
					}
					//if it's not sea port then include it into stations
					if (type.equalsIgnoreCase("Station") || type.equalsIgnoreCase("Dry Port"))
					{	
						stations.put(portNodeID,port);
						//adding the stations to the rail and road network
						//update nodeID and node database, the station is added to the network when handling edge is added to the network
						nodes.put(portNodeID, port);
					}
				}
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	private void parseCentroidtoStationConnection() {
		try {
			CSVReader reader = new CSVReader(new FileReader(centroid_to_station_dir), ',','#', 1);
			String[] nextLine;
			int centroid_station_index=1;
			while ((nextLine = reader.readNext()) != null) {
				
				int centroidID =Integer.valueOf(nextLine[0]);
				int portID   = Integer.valueOf(nextLine[6]);
				double distance=Double.valueOf(nextLine[7]);
				double speed =60; //speed of a truck is assumed 60km/h
				double time = distance/speed;
				
//				System.out.println("centroid id, port id: "+centroidID+","+portID);

				Centroid centroid = centroids.get(centroidID);				
				PortNode port = stations.get(portID);

				Pair<AbstractNode> endpoints= new Pair<AbstractNode>(centroid,port);
				RoadEdge edge = new RoadEdge (endpoints, centroid_station_index, "first and last mile transport", speed,time);
				
				//port hinterland  links
				centroid.addCentroidtoStationConnection(port, edge);
				centroid.setNearestStation(port);

				//port hinterland links are stored independently but not integrated to the network 
				//they are taken for visualization later
				this.centroidStationLinks.put(centroid_station_index,edge);
				centroid_station_index++;
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void createStationToRailLinks() {
		int stationToRailIndex=0;
		Iterator<PortNode> it = stations.values().iterator();		
		//searching for the closest rail node
		while (it.hasNext()) {
			PortNode portNode = (PortNode) it.next();
			Iterator<AbstractNode> mit = railNodes.values().iterator();
			AbstractNode closest = mit.next(); //calculate distance
			double shortestDistance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, closest)); 
			double distance;
			
			//FIXME:transloading speed
			double speed =60; //50 km/hour for trucks
//			double time=4; //4 hour
			
			//basic
			double time=4;
	
			AbstractNode candidate;
			while (mit.hasNext()){
				candidate = mit.next();
				distance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, candidate));

				if (distance < shortestDistance){
					closest = candidate;
					shortestDistance = distance;
//					time=shortestDistance/speed;
				}
			}
			Pair<AbstractNode> endpoints= new Pair<AbstractNode>(portNode, closest);
			
//			System.out.println ("port id, rail node id, distance:"+portNode.getId()+","+closest.getId()+","+shortestDistance);
			RoadEdge edge = new RoadEdge(endpoints, stationToRailIndex, "Station_Rail_Connector", speed, time);
			stationToRailIndex++;
			
			//adding the link from station to rail network  into the rail network graph
			this.railNetwork.addEdge(edge, endpoints);
		}
	}
	
	private void createRoadtoStationConnection() {
		int roadToStationIndex=0;
		Iterator<PortNode> it = stations.values().iterator();
		while (it.hasNext()) {
			PortNode portNode = (PortNode) it.next();
			Iterator<AbstractNode> mit = roadNodes.values().iterator();
			AbstractNode closest = mit.next(); //calculate distance
			double shortestDistance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, closest)); 
			double distance;
			//FIXME:waiting time speed
			double speed =60; //50 km/hour for trucks
			
			//basic
			double time=3; //4 hour
			
			AbstractNode candidate;
			while (mit.hasNext()){
				candidate = mit.next();
				distance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, candidate));

				if (distance < shortestDistance){
					closest = candidate;
					shortestDistance = distance;
//					time=shortestDistance/speed;
				}
			}
			Pair<AbstractNode> endpoints= new Pair<AbstractNode>(portNode, closest);
			
//			System.out.println ("port id, rail node id, distance:"+portNode.getId()+","+closest.getId()+","+shortestDistance);
			RoadEdge edge = new RoadEdge(endpoints, roadToStationIndex, "Road_Station_Connector", speed, time);
			roadToStationIndex++;
			
			//adding the link from station to rail network  into the rail network graph
			this.roadNetwork.addEdge(edge, endpoints);
		}
	}


	private void constructShortestPath() {
		System.out.println("Building shortest path engine");
		//setting up shortest paths for each mode
		
		Transformer<AbstractEdge, Double> seaEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			public Double transform(AbstractEdge edge)
			{
//				RoadEdge link =(RoadEdge)edge;
				AbstractNode first=edge.getEndpoints().getFirst();
				AbstractNode second= edge.getEndpoints().getSecond();
				double edgeCost=edge.getCosts();			
				return edgeCost;
			}
		};
		
		Transformer<AbstractEdge, Double> railEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			public Double transform(AbstractEdge edge)
			{
				RoadEdge link =(RoadEdge)edge;
				double edgeCost=link.getCosts();
				return edgeCost;
			}
		};
		
		Transformer<AbstractEdge, Double> roadEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			public Double transform(AbstractEdge edge)
			{
				RoadEdge link =(RoadEdge)edge;
				//road real distance means we use the database distance
				//for a start we can use haversine distance
				double edgeCost=link.getCosts();
				return edgeCost;
			}
		};
		
		this.seaShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.seaNetwork, seaEdgeCost, true);		
		this.roadShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.roadNetwork, roadEdgeCost, true);
		this.railShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.railNetwork, railEdgeCost, true);
	}
	
	public ODdata getBaseOD() {
		return baseOD;
	}

	public void setBaseOD(ODdata baseOD) {
		this.baseOD = baseOD;
	}

	public ArrayList<Commodity> getCommodities() {
		return commodities;
	}

	public void setCommodities(ArrayList<Commodity> commodities) {
		this.commodities = commodities;
	}


	public HashMap<Integer, AbstractNode> getNodes() {
		return nodes;
	}

	public void setNodes(HashMap<Integer, AbstractNode> nodes) {
		this.nodes = nodes;
	}

	public HashMap<Integer, AbstractEdge> getEdges() {
		return edges;
	}

	public void setEdges(HashMap<Integer, AbstractEdge> edges) {
		this.edges = edges;
	}

	public HashMap<Integer, PortNode> getPorts() {
		return ports;
	}
	
	public void setPorts(HashMap<Integer, PortNode> ports) {
		this.ports = ports;
	}


	public HashMap<Integer, Centroid> getCentroids() {
		return centroids;
	}
	
	public HashMap<String, Centroid> getCentroidsByName() {
		return centroidsByName;
	}

	public void setCentroids(HashMap<Integer, Centroid> centroids) {
		this.centroids = centroids;
	}

	public int getTimeHorizon() {
		return timeHorizon;
	}

	public void setTimeHorizon(int timeHorizon) {
		this.timeHorizon = timeHorizon;
	}

	public int getModes() {
		return modes;
	}

	public void setModes(int modes) {
		this.modes = modes;
	}
	
	public ModeShareCost getModeShareCost()
	{
		return this.modeShareCost;
	}
	
	//shortest path from port to port
	public synchronized List<AbstractEdge> getPathBetweenPorts(PortNode source, PortNode target){
		
//		System.out.println("source, destination: "+source.getName()+","+target.getName());
		return this.seaShortestPath.getPath(source, target);		
	}
	
	/**
	 * 
	 * @param source
	 * @param target
	 * @return list of edges that are being traversed to move between two ports
	 */
	public List<AbstractEdge> getPathSea(AbstractNode source, AbstractNode target){
		return this.seaShortestPath.getPath(source, target);
	}
	
	public List<AbstractEdge> getPathRoad(AbstractNode source, AbstractNode target){
		return this.roadShortestPath.getPath(source, target);
	}
	
	public List<AbstractEdge> getPathRail(AbstractNode source, AbstractNode target){
		if(railShortestPath.getPath(source, target)==null)
		{
			System.out.println("WARNING there is no path from,to: "+source.getName()+","+target.getName());
			return new ArrayList<AbstractEdge>();
		}
		return this.railShortestPath.getPath(source, target);	
	}
	
	
	public double getHinterlandShortestDistance (AbstractNode source, AbstractNode target)
	{
		double distance=0;
		List<AbstractEdge> portHinterland = this.roadShortestPath.getPath(source, target);
		
		for (AbstractEdge abstractEdge : portHinterland) {
			distance += abstractEdge.getLength();
//			System.out.println("distance: "+distance);
		}
		return distance;
	}
		
	public UndirectedSparseGraph<AbstractNode, AbstractEdge> getSeaNetworkGraph()
	{
		return this.seaNetwork;
	}
	
	public UndirectedSparseGraph<AbstractNode, AbstractEdge> getRoadNetworkGraph()
	{
		return this.roadNetwork;
	}
	
	public UndirectedSparseGraph<AbstractNode, AbstractEdge> getRailNetworkGraph()
	{
		return this.railNetwork;
	}
	
	public void resetSeaNetworkGraph()
	{
		Collection<AbstractEdge> edges =this.seaNetwork.getEdges();
		edges.parallelStream().forEach(edge-> edge.resetAssignedFlow());
	}
	
	public void resetRoadNetworkGraph()
	{
		Collection<AbstractEdge> edges =this.roadNetwork.getEdges();
		edges.parallelStream().forEach(edge-> edge.resetAssignedFlow());
	}
	
	public void resetRailNetworkGraph()
	{
		Collection<AbstractEdge> edges =this.railNetwork.getEdges();
		edges.parallelStream().forEach(edge-> edge.resetAssignedFlow());
	}

	public HashMap<Integer, HinterlandEdge> getPortHinterlandLinks() {
		return portHinterlandLinks;
	}


	public HashMap<Integer, String> getEdges_region() {
		return edges_region;
	}
	
	public double[][] getCarbonIntensity() {
		return carbonIntensity;
	}

	public int getCommoditySize() {
		return commoditySize;
	}

}//end of class
