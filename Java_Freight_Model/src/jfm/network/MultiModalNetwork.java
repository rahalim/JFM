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

import org.apache.commons.collections15.Transformer;

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


public class MultiModalNetwork {
	private ODdata baseOD;
	private ArrayList<Commodity> commodities;
	
	//2 versions of value to weight: original and logSum-based
	private ValueToWeight valToWeight;
	private ValueToWeightLogSum valToWeightLogSum;
	
	//2 versions of mode share model: original and cost-based
	private ModeShare modeShare;
	private ModeShareCost modeShareCost;
	//all edges in the database
	private HashMap<Integer, AbstractEdge> edges;
	
	//all nodes in the database
	private HashMap<Integer, AbstractNode> nodes;
	//all ports in the database
	private HashMap<Integer, PortNode> ports;
	//ports that are included in the network of the model
	private HashMap<Integer,PortNode> realPorts;
	
	private HashMap<Integer, AirportNode> airports;

	private HashMap<Integer, Centroid> centroids;
	private HashMap<String, Centroid> centroidsByName;
	private HashMap<Integer, HinterlandEdge> portHinterlandLinks;
	
	private HashMap<String,HashMap<String,double[]>> ODrelations;
	
	//directory of port cost
//	private String port_dir="input data/IFMCalibration/ports_with_cost_updated_2.csv";
	private String port_dir="input data/IFMCalibration/ports_cost_latest_round1.csv";
//	private String port_hinterland_dir="input data/IFMCalibration/port_hinterland_time_extra.csv";
	private String port_hinterland_dir="input data/IFMCalibration/port_hinterland_time_round1.csv";
	
	private String carbon_intensity_dir = "input data/CarbonIntensity/BAUCarbonIntensity.csv";
	
	private HashMap<Integer,MaritimeNode> maritimeNodes;
	
	private HashMap<Integer,String> edges_country = new HashMap<Integer,String>();
	private HashMap<Integer,String> edges_region = new HashMap<Integer,String>();
	
	//evolution of price, carbon intensity
	private double[] oilPrices = new double[6];
	private double[] gasPrices = new double[6];
	private double[][] carbonIntensity = new double[25][9];

	private Connection connection;
	private long endTime;
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> roadNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> roadShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> airNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> airShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> railNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> railShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> seaNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> seaShortestPath;
	
	private UndirectedSparseGraph<AbstractNode, AbstractEdge> waterwayNetwork;
	private DijkstraShortestPath<AbstractNode, AbstractEdge> waterwayShortestPath;
	
	//this needs to take an argument from the main 
	private boolean update_capacity=true;
	private int timeHorizon = 9;
	private int modes = 6;

	public MultiModalNetwork() throws SQLException
	{
		this.seaNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.airNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.roadNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.railNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.waterwayNetwork= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		this.portHinterlandLinks= new HashMap<Integer,HinterlandEdge>();		
	}//end of constructor

	public void buildMultimodalNetwork(Statement stm, Connection conn, long startTime) throws SQLException {
		this.connection= conn;
		System.out.println("Parsing commodities");
		this.commodities = new ArrayList<Commodity>();
		
		//Statement is an object that can be used to access the database server and execute querry
		//parsing the commodity database, row by row by looking at the column name
		ResultSet commoditySet=stm.executeQuery("SELECT * FROM Commodities_model ORDER BY ID ASC");
		while(commoditySet.next())
		{
			Commodity commodity =new Commodity();
			commodity.setID(Integer.valueOf(commoditySet.getString("ID")));
			commodity.setName(String.valueOf(commoditySet.getString("name")));
			commodity.setTransport(Integer.valueOf(commoditySet.getString("transport")));
			
			//model to indicate the commodity id used in the model (model value of -1 means that the commodity is not transported)
			commodity.setModel(Integer.valueOf(commoditySet.getString("model")));

			//active denotes mode of transport that is available
			commodity.getActive()[0]=Integer.valueOf(commoditySet.getString("air"));
			commodity.getActive()[1]=Integer.valueOf(commoditySet.getString("rail"));
			commodity.getActive()[2]=Integer.valueOf(commoditySet.getString("road"));
			commodity.getActive()[3]=Integer.valueOf(commoditySet.getString("sea"));
			commodity.getActive()[4]=Integer.valueOf(commoditySet.getString("sea2"));
			commodity.getActive()[5]=Integer.valueOf(commoditySet.getString("Waterways"));
			commodity.setTruckload(Double.valueOf(commoditySet.getString("Truck_load")));
			commodity.setTEU_load(Double.valueOf(commoditySet.getString("TEU_load")));
			commodity.setContainer(Integer.valueOf(commoditySet.getString("Container")));
			commodities.add(commodity);
		}	
		endTime=System.currentTimeMillis();
		long processTime= endTime-startTime;
		commoditySet.close();
		System.out.println("commodity is parsed, time: "+processTime+" ms");
	
		//parsing value weight model parameters
		System.out.println("Parsing value weight model parameters");
		
		valToWeight =new ValueToWeight();
		ResultSet vwSet=stm.executeQuery("SELECT * FROM Weight_model_ENV_2011 ORDER BY Code ASC");
		
		while(vwSet.next())
		{
			//parsing the weight of time for each commodity, based on different modes of transport
			int ID=Integer.valueOf(vwSet.getString("Code"));
			
			//coefficient for time for different commodity (ID), but they all share the same coefficient for the same mode of transport
			if(ID>=0)
			{
				valToWeight.getTime()[ID][0]=Double.valueOf(vwSet.getString("Air"));
				valToWeight.getTime()[ID][1]=Double.valueOf(vwSet.getString("Rail"));
				valToWeight.getTime()[ID][2]=Double.valueOf(vwSet.getString("Road"));
				valToWeight.getTime()[ID][3]=Double.valueOf(vwSet.getString("Sea"));
				valToWeight.getTime()[ID][4]=Double.valueOf(vwSet.getString("Sea2"));
				valToWeight.getTime()[ID][5]=Double.valueOf(vwSet.getString("Waterways"));
			}
			else
			{
				//parsing the weight parameters for each mode of transport
				String var=String.valueOf(vwSet.getString("Variable"));
				//constant for the trade to transport conversion formula
				if(var.equals("Intercept"))
				{
					valToWeight.getIntercept()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getIntercept()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getIntercept()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getIntercept()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getIntercept()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getIntercept()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//weight/coefficient for gdp percent origin
				if(var.equals("percento"))
				{
					valToWeight.getPercento()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getPercento()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getPercento()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getPercento()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getPercento()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getPercento()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//weight/coefficient for gdp percent destination 
				if(var.equals("percentd"))
				{
					valToWeight.getPercentd()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getPercentd()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getPercentd()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getPercentd()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getPercentd()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getPercentd()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//weight/coefficient for gdp percent per capita origin 
				if(var.equals("percentgdpco"))
				{
					valToWeight.getPercentgdpco()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getPercentgdpco()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getPercentgdpco()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getPercentgdpco()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getPercentgdpco()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getPercentgdpco()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//weight/coefficient for gdp percent per capita destination
				if(var.equals("percentgdpcd"))
				{
					valToWeight.getPercentgdpcd()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getPercentgdpcd()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getPercentgdpcd()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getPercentgdpcd()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getPercentgdpcd()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getPercentgdpcd()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//weight/coefficient for log of gdp percent per capita origin/gdp percent per capita destination 
				if(var.equals("lngdpcod"))
				{
					valToWeight.getLngdpcod()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getLngdpcod()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getLngdpcod()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getLngdpcod()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getLngdpcod()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getLngdpcod()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//coefficient for average distance
				if(var.equals("AvDistance"))
				{
					valToWeight.getAvdist()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getAvdist()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getAvdist()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getAvdist()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getAvdist()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getAvdist()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//coefficient for language
				if(var.equals("Language"))
				{
					valToWeight.getLang()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getLang()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getLang()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getLang()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getLang()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getLang()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//trade agreement coefficient
				if(var.equals("RegTrade"))
				{
					valToWeight.getRta()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getRta()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getRta()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getRta()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getRta()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getRta()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
				//coefficient for if the countries are neighbour
				if(var.equals("Contig"))
				{
					valToWeight.getContig()[0]=Double.valueOf(vwSet.getString("Air"));
					valToWeight.getContig()[1]=Double.valueOf(vwSet.getString("Rail"));
					valToWeight.getContig()[2]=Double.valueOf(vwSet.getString("Road"));
					valToWeight.getContig()[3]=Double.valueOf(vwSet.getString("Sea"));
					valToWeight.getContig()[4]=Double.valueOf(vwSet.getString("Sea2"));
					valToWeight.getContig()[5]=Double.valueOf(vwSet.getString("Waterways"));
				}
			}
		}	
		vwSet.close();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("value_weight model is parsed, time: "+processTime+" ms");
				
		//parsing value mode share model parameters
		System.out.println("Parsing mode share model parameters");
		modeShare = new ModeShare();
		ResultSet modeShareSet=stm.executeQuery("SELECT * FROM Mode_share_model ORDER BY [Code] ASC");
//		ResultSet modeShareSet=stm.executeQuery("SELECT * FROM Mode_share_model_Experiment ORDER BY [Code] ASC");
		while(modeShareSet.next())
		{
			String code=String.valueOf(modeShareSet.getString("Code"));
			double value=Double.valueOf(modeShareSet.getString("Value"));

			//parsing mode share value, only looking for mode share by air, rail, road and sea	
			//asc:alternative specific coefficient for each mode of transport
			//at the moment asc is set to zero
			if(code.equals("Air"))
				modeShare.getAsc()[0]=value;
			else if(code.equals("Rail"))
				modeShare.getAsc()[1]=value;
			else if(code.equals("Road"))
				modeShare.getAsc()[2]=value;

			//waterways asc is similar to sea +0.5
			//+0.5 value was for experiment
			else if(code.equals("Sea"))
			{
				//asc for sea
				modeShare.getAsc()[3]=value;
				//asc for sea 2
				modeShare.getAsc()[4]=value;
				//asc for waterways
				modeShare.getAsc()[5]=value+0.5;
			}

			//parsing distance coefficient for each mode of transport, we only use distance
			else if(code.equals("DIST_A"))
				//coefficient for air distance 
				modeShare.getDist()[0]=value;
			else if(code.equals("DIST_RL"))
				//coefficient for rail distance
				modeShare.getDist()[1]=value;
			else if(code.equals("DIST_R"))
			{
				//coefficient for road distance
				modeShare.getDist()[2]=value;
				//coefficient for inland waterways, experiment
				modeShare.getDist()[5]=value*0.75;
			}
			//coefficient for sea distance, currently they are zero and sea mode utility is based on time
			else if(code.equals("DIST_S"))	
			{			
				//sea 1
				modeShare.getDist()[3]=value;
				//sea 2
				modeShare.getDist()[4]=value;
			}
			else
			{	//time coefficient for each commodity 
				int ID=Integer.valueOf(code);
				modeShare.getTime()[ID]=value;
			}
		}
		modeShareSet.close();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("mode share model is parsed, time: "+processTime+" ms");	

		//cost-based mode share
//		buildModeShare(stm);
		
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("cost-based mode share model is parsed, time: "+processTime+" ms");	
		
		//parsing nodes
		System.out.println("Parsing nodes");
		ResultSet node_set=stm.executeQuery("SELECT * FROM Nodes_of_Complete_network_2016 ORDER BY [NodeID] ASC");
		ports = new HashMap<Integer, PortNode>();
		airports = new HashMap<Integer, AirportNode>();
		nodes = new HashMap<Integer, AbstractNode>();
		edges = new HashMap<Integer, AbstractEdge>();
				
		while(node_set.next())
		{
			//nodes can be airport or port node	or normal node	
			int ID=Integer.valueOf(node_set.getString("NodeID"));
			int flights=Integer.valueOf(node_set.getString("Flights"));
			double lat=Double.valueOf(node_set.getString("Geometry_YLO"));
			double longf=Double.valueOf(node_set.getString("Geometry_XLO"));	
			String portName = node_set.getString("Port_name");
			String airportName = node_set.getString("Airport_name");
			String countryISO = node_set.getString("ISO");
			String seaArea= String.valueOf(node_set.getString("Sea_area"));
			
			//parsing time penalty of the node, this is fixed time cost at the node
			double cost[] = new double[5];
			cost[0]=Double.valueOf(node_set.getString("Cost_containers"));
			cost[1]=Double.valueOf(node_set.getString("Cost_dry_bulk"));
			cost[2]=Double.valueOf(node_set.getString("Cost_liquid_bulk"));
			cost[3]=Double.valueOf(node_set.getString("Cost_general_cargo"));
			cost[4]=Double.valueOf(node_set.getString("Cost_roro"));
		
			//parsing capacity of the node
			double capacity[] = new double[5];
			capacity[0]=Double.valueOf(node_set.getString("Capacity_containers"));
			capacity[1]=Double.valueOf(node_set.getString("Capacity_dry_bulk"));
			capacity[2]=Double.valueOf(node_set.getString("Capacity_liquid_bulk"));
			capacity[3]=Double.valueOf(node_set.getString("Capacity_General_Cargo"));
			capacity[4]=Double.valueOf(node_set.getString("Capacity_RoRo"));

			//to ensure all nodes are included in the map
			AbstractNode node = new AbstractNode(lat,longf,"node",ID);
			//TODO: check the consistency of node ID across all routines
			nodes.put(ID, node);
			
			//handling time initiation for all nodes
			double[] handlingTime = new double[5];
			//TODO: handling time_ref is not used
			double[] handlingTime_ref = new double[5];
			
			//!Activate for running shortest path
		    //if node is a port node then set cost into -1.0,
			//this is done to create a uniform penalty for all the ports
			if(portName.equalsIgnoreCase("-")==false)
			{
//				System.out.println("portName is:"+portName);
				for(int i=0;i<5;i++)
				{
					cost[i]=-1.0;
				}
			}
			
			//updating reference cost based on the minimum cost of all nodes
			double ref_cost=1000.0;	
			for(int i=0;i<5;i++)
			{
				if(cost[i]<ref_cost)
					ref_cost=cost[i];
			}
			//adding manually small logical ports that need to be included to allow all centroids to have their closest ports
			//please refer to the ports_centroid_id_input.csv for the list	
			if(portName.equalsIgnoreCase("-")==false && seaArea.equalsIgnoreCase("Norh_America_lakes")==false)
			{
//				System.out.println("creating port, portName is:"+portName);
				double P_ref_cost;			
				boolean active=true;
				double transhipmentTime;
				double costIndicator= 0;
				
				//setting ports with negative cost into inactive 
				for(int i=0;i<5;i++)
				{
					//if cost is negative,set handling time based on capacity
					if(cost[i]<0)
					{
						//setting the handling time of the ports
						handlingTime[i]= 0.1*312.82*Math.exp(-0.000006*capacity[i]);
						handlingTime_ref[i]= 0.1*312.82*Math.exp(-0.000006*capacity[i]);	
					}
					//if the cost is non-negative then handling time is uniform
					else
					{
						handlingTime[i]= 1000.0;
						handlingTime_ref[i]=1000.0;
					}
					
					//setting the handling time at all ports to zero				
					//!activate for running the shortest path
					handlingTime[i]= 0.0;
					handlingTime_ref[i]=0.0;
					
					//if there is a time cost that is not negative for any of the commodities then the port is inactive
					costIndicator+=cost[i];	
				}
				
				//if none of the port has negative cost then it should not be activated
				if(costIndicator>=0)
					active=false;
				
				//setting port reference cost values as the lowest ref_cost of the node
				//TODO: but this goes nowhere
				P_ref_cost=ref_cost;

				//transhipment time calculation
				//if the node capacity for containers is below 1, set node transshipment time to be 5000 (penalized for congestion)
				if(capacity[0]<1)
					transhipmentTime=5000;
				//if the node does have a capacity set transhipment time based on the function given below
				else
					transhipmentTime= -0.3767857143*0.05*capacity[0]*0.5;

				//special conditions for transhipment time based on port capacity
				if(capacity[0]<9000.0)
					transhipmentTime+=1000;
				else if (capacity[0]>30000)
					transhipmentTime= capacity[0]/3000;

				//!activate for running the shortest path, it basically eliminates all the penalty
				transhipmentTime=0.0;

				//creating the port node
				//TODO: the reference cost is not really used 
				// the original version includes the reference cost
				if(ref_cost<0)
				{
//					System.out.println("port created: "+portName+" id: "+ID);
//					PortNode port = new PortNode(lat,longf,portName,ID,portName,countryISO,
//							capacity,cost,cost, transhipmentTime,handlingTime,0.0,0.0,active, seaArea);
//					ports.put(ID, port);
//					nodes.put(ID, port);
				}
			}//end condition for port node

			//create airport node
			//TODO: strict requirements to limit airport size
			if(airportName.equalsIgnoreCase("-")==false && flights>=4)
			{
				//setting handling time to be zero for airports
				for(int i=0;i<5;i++)
				{
					handlingTime[i]= 0.0;
					handlingTime_ref[i]=0.0;
				}
				
				AirportNode airport = new AirportNode(lat,longf,airportName,ID,airportName,countryISO,
						capacity,cost,cost, handlingTime);
				
//				System.out.println("airport is created: "+airport.getName());

				//if flights<=4 set the airport to be inactive
				if(airport.getFlights()<=80)
					airport.setActive(false);
				
				airports.put(ID, airport);
				nodes.put(ID, airport);
			}

			//all nodes that don't belong to port or airport node
			//this can be road node, maritime node, centroids,etc
			//TODO: classification is needed for this
			//extra specification for the node cost is also needed
			if(portName.equalsIgnoreCase("-") && airportName.equalsIgnoreCase("-"))
			{
//				System.out.println("REST of the case airportName is:"+airportName);
				//setting handling time to be zero for all other nodes
				for(int i=0;i<5;i++)
				{
					handlingTime[i]= 0.0;
					handlingTime_ref[i]=0.0;
				}
				AbstractNode otherNode = new AbstractNode(lat,longf,"unclassified",ID, handlingTime);
				nodes.put(ID, otherNode);
			}
		}
		
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("nodes are parsed, time: "+processTime+" ms");

		System.out.println("Parsing expansion plans");
		//if update capacity is true then adding expansion of capacity to the nodes
		if(update_capacity==true)
		{
			System.out.println("setting expansion capacity");
			ResultSet expSet=stm.executeQuery("SELECT * FROM Nodes_expansion ORDER BY [NodeID] ASC");
			while(expSet.next())
			{
				int ID=Integer.valueOf(expSet.getString("NodeID"));
				double exp=Double.valueOf(expSet.getString("Max_Capacity_expansion"));	

				//TODO: check the consistency of the node ID
				AbstractNode node =nodes.get(ID);
				//add expansion for nodes
				for(int y=2010;y<=2050;y+=5)
				{
					node.getExpansion().add(Math.pow(exp+1,(y-2010)));
				}

			}
			expSet.close();

			//for all nodes
			for(AbstractNode node : nodes.values())
			{
				//if the expansion set doesn't contain anything
				if(node.getExpansion().size()==0)
				{
					for(int y=2010;y<=2050;y+=5)
					{	
						//set expansion value to be one for every five years
						node.getExpansion().add(1.0);
					}
				}
			}

			//special for node id 95473, set the 8th expansion to 1.6764
			nodes.get(95473).getExpansion().set(8,1.6764);
		}
		//if capacity is not updated then set expansion for every 5 years to be 1
		else
		{
			System.out.println("update_capacity false, adding uniform expansion plan");
			
			for(AbstractNode node : nodes.values())
			{
				for(int y=2010;y<=2050;y+=5)
				{
//					System.out.println("expansion for node: "+node.getId());
					node.getExpansion().add(1.0);
				}
			}	
		}
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("node expansion plan is parsed, time: "+processTime+" ms");
		
		//parsing centroid
		System.out.println("Parsing centroids");
		ResultSet centroidSet= stm.executeQuery("SELECT [Pop],[iso],[CITY_NAME],[NodeId],[ID],[GDP],[factor],[Region2015] FROM Centroids_2017 ORDER BY [ID] ASC");
		
		centroids = new HashMap<Integer,Centroid>();
		centroidsByName = new HashMap<String,Centroid>();
				
		Statement stm2=conn.createStatement();
		String query;
		//assigning values of the centroid attributes from the database to the objects 
		while(centroidSet.next())
		{
			int centroidID=Integer.valueOf(centroidSet.getString("ID"));
			String name = String.valueOf(centroidSet.getString("CITY_NAME"));
			String region = String.valueOf(centroidSet.getString("Region2015"));
			String ISO = String.valueOf(centroidSet.getString("iso"));
			double factor = Double.valueOf(centroidSet.getString("factor"));
			
			//node that is associated with the centroid
			//TODO: check the node id
			int nodeID= Integer.valueOf(centroidSet.getString("NodeId"));
			AbstractNode node=nodes.get(nodeID);
			double lat = node.getLat();
			double lon = node.getLon();
			
			//setting the centroid name by force into the node
			//TODO: but this is not relevant
			node.setName(name);
			node.setID(centroidID);

			//GDP data and population data for centroids, they need to be updated whenever new data is available
			double GDP=Double.valueOf(centroidSet.getString("GDP"));
			double Pop=Double.valueOf(centroidSet.getString("Pop"));
			double GDP_Pop = GDP/Pop;
			double[] GDPSeries = new double[timeHorizon+1];
			double[] GDPCapitaSeries = new double[timeHorizon+1];

			query = "SELECT count(*) FROM GDP_growth_countries_2016 WHERE Country='" + ISO +"'";
			double gdpCount = getValueofQuery(stm2, query);
			
			query = "SELECT count(*) FROM GDP_capita_2016 WHERE Country='" + ISO +"'";
			double GDP_capita_count = getValueofQuery(stm2, query);
			
			query = "SELECT [" + (2010) + "] FROM GDP_capita_2016 WHERE Country='" + ISO +"'";
			double GDP_reference = getValueofQuery(stm2, query);
			
			//for each GDP time series (2010, 2015,2020,2025,2030,2035,2040,2045,2050)
			for(int j=0;j<timeHorizon;j++)
			{
				double GDP_ref=0;
				query="SELECT [" + (2010+j*5) + "] FROM GDP_growth_countries_2016 WHERE Country='" + ISO +"'";
				double GDP_Growth = getValueofQuery(stm2, query);
				
				//assigning the GDP for each centroid
				//if GDP growth of a country is>0
				if(gdpCount>0)
				{
					GDPSeries[j]=GDP*GDP_Growth;
				}
				else
				{
					//if gdp growth is less than zero
					GDPSeries[j]=GDP*Math.pow(1.02,j*5);
				}

				query ="SELECT [" + (2010+j*5) + "] FROM GDP_capita_2016 WHERE Country='" + ISO +"'";
				double GDP_capita = getValueofQuery(stm2, query);
				
				//assigning the GDP per capita for each centroid
				//if gdp per capita of a certain country is more than zero
				if(GDP_capita_count>0 && GDP_capita>0)
				{
					GDP_ref=GDP_reference;
					GDPCapitaSeries[j]=GDP_Pop*GDP_capita/GDP_ref;
				}
				else
				{//if gdp per capita is less than zero, use the following formula
					GDPCapitaSeries[j]=GDP_Pop*Math.pow(1.02,j*5);
//					System.out.println("query is "+query+" GDPCapita: "+j+" value is: "+GDPCapitaSeries[j]);
				}
			}
			//creation of centroid
			Centroid centroid = new Centroid(name,ISO,region,lat, lon,centroidID,factor,GDPSeries, GDPCapitaSeries);
			
			//adding centroid to the node maps
			centroids.put(centroidID, centroid);
			nodes.put(nodeID, centroid);
			centroidsByName.put(name, centroid);
			//TODO: temporary check
			this.seaNetwork.addVertex(centroid);
		}
	
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("centroids are parsed, time: "+processTime+" ms");
		centroidSet.close();	
	
		ResultSet edge_country_set= stm.executeQuery("SELECT * FROM [Freight_Network_Model_2015].[dbo].[Edges_country] "
				+ "where  mode ='road' or mode ='rail'");

		while(edge_country_set.next())
		{
			//parsing the id
			int edgeID=Integer.valueOf(edge_country_set.getString("EdgeID"));
			//					String mode=String.valueOf(edges_country.getString("Mode"));
			String ISO=String.valueOf(edge_country_set.getString("ISO"));
			String Region=String.valueOf(edge_country_set.getString("Group_Name"));
			this.edges_country.put(edgeID,ISO);
			this.edges_region.put(edgeID,Region);
		}
		edge_country_set.close();

		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("edge to country is parsed");


		System.out.println("Parsing road network");
		
		//parsing road network
		ResultSet roadsdata_set= stm.executeQuery("SELECT [OpenID],[OpenId2],[PenF],[Mode],[EdgeID],[Length],[Penalty],"
				+ "[FromNodeID],[ToNodeID],[Active],[Capacity] FROM Edges_of_Complete_network_2016 ORDER BY [EdgeID] ASC");		
		
		while(roadsdata_set.next())
		{
			//parsing road edge ids
			int EdgeId=Integer.valueOf(roadsdata_set.getString("EdgeID"));
			String region = this.edges_region.get(EdgeId);
			
			//special ID created for bidirectional road edges
			// to link to shape file		
			int ID3=Integer.valueOf(roadsdata_set.getString("OpenID"));
			//parsing the mode of the road network 
			//(here road edge is a virtual edge that represents different modes of transport)
			String mode = String.valueOf(roadsdata_set.getString("Mode"));
			double length= Double.valueOf(roadsdata_set.getString("Length"));
			double capacity=Double.valueOf(roadsdata_set.getString("Capacity"));
			
			//denoting whether the edge is active or not
			int active=Integer.valueOf(roadsdata_set.getString("Active"));
			
			//the real trip time
			double time=Double.valueOf(roadsdata_set.getString("Penalty"));
			//penalty value
			double penf=Double.valueOf(roadsdata_set.getString("PenF"));
			
			//TODO: check the consistency of the node ID
			AbstractNode fromNode=nodes.get(Integer.valueOf(roadsdata_set.getString("FromNodeID")));
		  	AbstractNode toNode= nodes.get(Integer.valueOf(roadsdata_set.getString("ToNodeID")));
		  	Pair<AbstractNode> endpoints= new Pair<AbstractNode>(fromNode,toNode);		  	
		  	
		  	//TODO: the edge object needs to be differentiated based on the mode
		  	//the penalty can then be assigned as a generic variable
		  	//as well as the handling, transhipment, and time cost
		  	//road edge construction
			RoadEdge road = new RoadEdge(endpoints, EdgeId, mode, length, capacity);
//			RoadEdge road2 = new RoadEdge(endpoints, ID2, EdgeId, ID4, mode, length, capacity);
			
			//assigning the general trip time values
			//if the mode of transport is not active then trip time using this mode of transport will be penalized
			road.setTripTime(time+(1-active)*1000000 + penf);
		
			
			//putting all edges in one hashmap
			this.edges.put(EdgeId, road);
				
			//assigning penalty time value for air transport
//			if(road.getMode().equalsIgnoreCase("Air"))
//			{
//				//since this is air mode of transport we set huge time penalties to other mode of transport
////				System.out.println("this is air mode, edge mode is: "+road.getMode());
//				double penalty=Double.MAX_VALUE;
//				road.setPen_air(0);
//				road.setPen_road(penalty);
//				road.setPen_rail(penalty);
//				road.setPen_sea(penalty);
//				road.setPen_sea2(penalty);
//				road.setPen_waterways(penalty);	
//				road.setPen_access(penalty);
//				
//				this.airNetwork.addEdge(road, endpoints);
//			}
//			
//			//assigning penalty time value for road transport
//			else if(road.getMode().equalsIgnoreCase("Road"))
//			{
//				road.setPen_road(0);
//				road.setPen_air(time*100);				
//				road.setPen_rail(time*100);
//				road.setPen_sea(time*100);
//				road.setPen_sea2(time*5);
//				road.setPen_waterways(time*50);	
//				road.setPen_access(time*80);
//				
//				this.airNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road, endpoints);
//				this.roadNetwork.addEdge(road, endpoints);
//				//TODO: deactivate road network because the predefined hinterland connection
////				this.seaNetwork.addEdge(road, endpoints);
//			}
//			
//			//assigning penalty time for rail transport
//			else if(road.getMode().equalsIgnoreCase("Rail"))
//			{
//				road.setPen_road(time*100);
//				road.setPen_air(time*100);				
//				road.setPen_rail(0);
//				road.setPen_sea(time*70);
//				road.setPen_sea2(time*3);
//				road.setPen_waterways(time*50);	
//				road.setPen_access(time*45);
//				
//				this.roadNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road, endpoints);
//				//TODO: deactivate road network because the predefined hinterland connection
////				this.seaNetwork.addEdge(road, endpoints);
////				System.out.println("rail length:"+road.getRoadRealDistance());
//			}
//				
//			else if(road.getMode().equalsIgnoreCase("Sea"))
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(0);
//				road.setPen_sea2(0);
//				road.setPen_waterways(Double.MAX_VALUE);
//				road.setPen_access(Double.MAX_VALUE);
//			//this has been replaced by simplified maritime network down below	
////				this.seaNetwork.addEdge(road, endpoints);
//			}
//			
//			else if(road.getMode().equalsIgnoreCase("Ferry"))
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(2400*5);
//				road.setPen_sea2(2400*5);
//				road.setPen_waterways(Double.MAX_VALUE);
//				road.setPen_access(20*time);
//				
////				this.seaNetwork.addEdge(road, endpoints);
//				this.roadNetwork.addEdge(road, endpoints);
//			}
//			
//			else if(road.getMode().equalsIgnoreCase("River")) 
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(0);
//				road.setPen_sea2(0);
//				road.setPen_waterways(0);
//				road.setPen_access(time*20);
//				this.railNetwork.addEdge(road, endpoints);
//				this.roadNetwork.addEdge(road, endpoints);
//				this.waterwayNetwork.addEdge(road, endpoints);
//			}
//			
//			//assigning time penalty associated with transfer of modes between rail and road
//			else if(road.getMode().equalsIgnoreCase("Rail_Road_Conn"))
//			{		
//				road.setPen_air(time*100);
//				//if road wants to change to rail, it must be only if there's no other way
//				//to get to the destination except via rail
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(time*100);
////				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(2);
//				road.setPen_sea2(2);
////				road.setPen_sea(200);
////				road.setPen_sea2(200);
//				road.setPen_waterways(Double.MAX_VALUE);
//				road.setPen_access(2);
//				
//				this.roadNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road, endpoints);
////				this.seaNetwork.addEdge(road, endpoints);
//			}
//			
//			//connector between airport to port
//			else if(road.getMode().equalsIgnoreCase("Air_Port_Conn"))
//			{
//				road.setPen_air(1000);
//				road.setPen_road(1000000);
//				road.setPen_rail(1000000);
//				road.setPen_sea(1000000);
//				road.setPen_sea2(1000000);
//				road.setPen_waterways(1000000);
//				road.setPen_access(Double.MAX_VALUE);
//				this.airNetwork.addEdge(road, endpoints);
//			}
//			
//			//connector between airport to road nodes
//			else if(road.getMode().equalsIgnoreCase("Air_Conn"))
//			{
//				road.setPen_air(0);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(Double.MAX_VALUE);
//				road.setPen_sea2(Double.MAX_VALUE);
//				road.setPen_waterways(Double.MAX_VALUE);
//				road.setPen_access(Double.MAX_VALUE);
//				
//				this.airNetwork.addEdge(road, endpoints);
//			}
//			
//			//connector between airport and centroid, when road network is absent
//			else if(road.getMode().equalsIgnoreCase("Air_Ports_Centroid_Conn"))
//			{
//				road.setPen_road(1000000);
//				road.setPen_rail(1000000);
//				road.setPen_sea(1000000);
//				road.setPen_sea2(1000000);
//				road.setPen_waterways(1000000);
//				road.setPen_access(1000);
//				
//				this.airNetwork.addEdge(road, endpoints);
//			}
//			
//			//transfer between centroid to road
//			//the centroid node is an abstractNode instead of a Centroid object
//			//this means the graph needs to include the abstractNode object
//			else if(road.getMode().equalsIgnoreCase("Centroid_Road_Conn"))
//			{
//				road.setPen_air(1000);
//				road.setPen_road(1000);
//				road.setPen_rail(1000);
//				road.setPen_sea(1000);
//				road.setPen_sea2(1000);
//				road.setPen_waterways(1000);
//				road.setPen_access(1000);
//				
//				this.airNetwork.addEdge(road, endpoints);
//				this.roadNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road, endpoints);
//				//TODO: This is deactivated bc the centroid to road connection is not necessary anymore
////				this.seaNetwork.addEdge(road, endpoints);
//				this.waterwayNetwork.addEdge(road, endpoints);
//			}
//			
//			//transfer between port to road
//			else if(road.getMode().equalsIgnoreCase("Ports_Road_Conn")) 
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(5);
//				road.setPen_sea2(5);
//				road.setPen_waterways(5);
//				
//				road.setPen_access(50);
//				//TODO: This is deactivated bc the centroid to road connection is not necessary anymore
////				this.seaNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road, endpoints);
//				this.roadNetwork.addEdge(road, endpoints);
//			}
//			
//			//transfer between river to road
//			else if(road.getMode().equalsIgnoreCase("River_road_conn"))
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
////				road.setPen_sea(500);
////				road.setPen_sea2(500);
//				road.setPen_sea(5);
//				road.setPen_sea2(5);
//				road.setPen_waterways(5);
//				road.setPen_access(5);
//				
//				this.roadNetwork.addEdge(road, endpoints);
////				this.waterwayNetwork.addEdge(road, endpoints);
////				this.seaNetwork.addEdge(road, endpoints);
//				this.railNetwork.addEdge(road,endpoints);
//			}
//			
//			//transfer between port to maritime network
//			else if(road.getMode().equalsIgnoreCase("Port_Shipping_Conn") || road.getMode().equalsIgnoreCase("River_Sea_Conn"))
//			{
//				road.setPen_air(Double.MAX_VALUE);
//				road.setPen_road(Double.MAX_VALUE);
//				road.setPen_rail(Double.MAX_VALUE);
//				road.setPen_sea(0.5);
//				road.setPen_sea2(0.5);
//				road.setPen_waterways(Double.MAX_VALUE);
//				road.setPen_access(Double.MAX_VALUE);
//				this.roadNetwork.addEdge(road, endpoints);
//			}
//			//!what are these?
//			else
//			{
//				System.out.println("road mode is:"+road.getMode());
//				road.setPen_air(time*5000);
//				road.setPen_road(time*5000);
//				road.setPen_rail(time*5000);
//				road.setPen_sea(time*5000);
//				road.setPen_sea2(time*5000);
//				road.setPen_waterways(time*5000);
//				road.setPen_access(time*5000);
////				this.seaNetwork.addEdge(road, endpoints);
//			}	
			
			//assigning the trip time to r2 which is the same with r1
//			road2.setTripTime(time);
//			road2.setTripTime_real(road.getTrip_time_real());		  	
//		  	road2.setPen_air(road.getPen_air());
//		  	road2.setPen_road(road.getPen_road());
//			road2.setPen_rail(road.getPen_rail());
//			road2.setPen_sea(road.getPen_sea());
//			road2.setPen_sea2(road.getPen_sea2());
//			road2.setPen_waterways(road.getPen_waterways());
//			road2.setPen_access(road.getPen_access());	
		}
		constructShortestPath();
	
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("roads are parsed, time: "+processTime+" ms");
		roadsdata_set.close();	
		
		System.out.println("Parsing IEA oil price");
		ResultSet oil_prices_set= stm.executeQuery("SELECT * FROM IEA_crude_oil_price  "
				+ "where year >= 2010 and year <=2035 ORDER BY [year] ASC");
		int yearIndex=0;
		while (oil_prices_set.next())
		{
			oilPrices[yearIndex]= Double.valueOf(oil_prices_set.getString("adapted"));
//			System.out.println("oil price for: "+yearIndex+" is "+oilPrices[yearIndex]);
			yearIndex++;
		}
		oil_prices_set.close();
		
		System.out.println("Parsing IEA gas price");
		ResultSet gas_prices_set= stm.executeQuery("SELECT * FROM IEA_gas_price  "
				+ "where year >= 2010 and year <=2035 ORDER BY [year] ASC");
		int yearInt=0;
		while (gas_prices_set.next())
		{
			gasPrices[yearInt]= Double.valueOf(gas_prices_set.getString("corrected_price"));
//			System.out.println("gas price for: "+yearInt+" is "+gasPrices[yearInt]);
			yearInt++;
		}
		gas_prices_set.close();
		
		System.out.println("Parsing carbon intensity");
		parseCarbonIntensity();
		
		
		//parsing maritime nodes
		System.out.println("Parsing simplified maritime network");
		maritimeNodes= new HashMap<Integer, MaritimeNode>();
		ResultSet maritime_nodes_set= stm.executeQuery("SELECT * FROM maritimenodes ORDER BY [NODE] ASC");
		while (maritime_nodes_set.next())
		{
			int nodeID=Integer.valueOf(maritime_nodes_set.getString("NODE"));
			double lat=Double.valueOf(maritime_nodes_set.getString("LATITUDE"));
			double lon=Double.valueOf(maritime_nodes_set.getString("LONGITUDE"));
			
			MaritimeNode node = new MaritimeNode(lat,lon,"maritime node",nodeID);
			maritimeNodes.put(nodeID,node);
		}
		maritime_nodes_set.close();
		
		//parsing maritime links
		ResultSet maritime_links_set= stm.executeQuery("SELECT * FROM maritimelinks ORDER BY [FROM] ASC");	
		int index =1001;
		while (maritime_links_set.next())
		{
			int fromID =Integer.valueOf(maritime_links_set.getString("FROM"));
			int toID   =Integer.valueOf(maritime_links_set.getString("TO"));
			MaritimeNode from=maritimeNodes.get(fromID);
			MaritimeNode to = maritimeNodes.get(toID);
			
			Pair<AbstractNode> endpoints= new Pair<AbstractNode>(from,to);
			MaritimeEdge  edge = new MaritimeEdge(endpoints,0.0,0.0,0.0, index,"Sea");
			this.seaNetwork.addEdge(edge, endpoints);
			index++;
		}
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("maritime network is parsed, time: "+processTime+" ms");
		maritime_links_set.close();
		
		//ports here are used to obtain/query the ports in the bigger pool 
		//and to set the handling cost of those ports
		System.out.println("Parsing ports");
		//TODO: this section is deactivated untill the right parameter values are found and that these values are moved into the sql server
		//before then we use csv file that allows the values to be modified manually
		
//		realPorts = new HashMap<Integer,PortNode>();
//		ResultSet port_set = stm.executeQuery("SELECT * FROM ports_with_cost_2");
//		//new index for the ports for calibration purpose
//		int varPortId=1;
//		while (port_set.next())
//		{
//			int portID   = Integer.valueOf(port_set.getString("port_id"));
//			int realID = Integer.valueOf(port_set.getString("id"));
//			String portName = String.valueOf(port_set.getString("port_name"));
//			double handlingCost = Double.valueOf(port_set.getString("port_cost"));
//			double observedThroughput = Double.valueOf(port_set.getString("gateway_throughput"));
//			
//			PortNode port = ports.get(portID);
//			port.setHandlingCosts(handlingCost);
//			port.setObservedThroughput(observedThroughput);
//			
//			System.out.println("port id, solution id, port name, port_name2, handling cost, observed throughput:"
//					+portID+","+realID+","+portName+","+port.getLabel()+","+handlingCost+","+observedThroughput);
//			
//			this.seaNetwork.addVertex(port);
//			//adding ports that are used in the model into a special data structure with a new id
//			realPorts.put(realID,port);
//			varPortId++;
//		}
				
		parsePortFile();
		System.out.println("Parsing port-hinterland connections");
		
//		//these hinterland connections are only added to the maritime network
//		ResultSet portHinterland_set= stm.executeQuery("SELECT * FROM port_hinterland_final2"
//				+ " where rank<=3 or distance<=1500"
//				+ " ORDER BY [centroid_id] ASC");
//		int hinterlandIndex=1;
//		while (portHinterland_set.next())
//		{
//			int centroidID =Integer.valueOf(portHinterland_set.getString("centroid_id"));
//			int portID   = Integer.valueOf(portHinterland_set.getString("port_id"));
//			String country = portHinterland_set.getString("country");
//			double distance=Double.valueOf(portHinterland_set.getString("distance"));
//			double capacity=Double.valueOf(portHinterland_set.getString("capacity_container"));
//			int rank = Integer.valueOf(portHinterland_set.getString("rank"));
//			
//			//condition to limit the number of ports included in the network
//			Centroid centroid = centroids.get(centroidID);
//			//here only ports that are part of the hinterland that are included in the hinterland connections
//			PortNode port = ports.get(portID);
//			
//			Pair<AbstractNode> endpoints= new Pair<AbstractNode>(centroid,port);
//			HinterlandEdge edge = new HinterlandEdge (endpoints, TransportMode.CONTINENTAL, distance);
//			//setting the id of the hinterland edge based on the hinterland index, so that it can be used to 
//			//port hinterland  links
//			edge.setId(hinterlandIndex);
//			centroid.addHinterlandConnection(port, edge);
//			
//			//port hinterland links are stored independently but not integrated to the network 
//			//they are taken for visualization later
//			this.portHinterlandLinks.put(hinterlandIndex,edge);
//			hinterlandIndex++;
//		}	
		parsePortHinterlandFile();		
		System.out.println("port-hinterland connections are parsed");
		
		
		//creating port maritime connection based on parsed ports
		int pmEdgeIndex=0;
		System.out.println("creating port-maritime connections");
		Iterator<PortNode> it = realPorts.values().iterator();

		//searching for the closest port node
		while (it.hasNext()) {
			PortNode portNode = (PortNode) it.next();

			// iterate over all maritime nodes
			Iterator<MaritimeNode> mit = maritimeNodes.values().iterator();
			MaritimeNode closest = mit.next(); //calculate distance
			double shortestDistance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, closest)); 
			double distance;
			MaritimeNode candidate;
			while (mit.hasNext()){
				candidate = mit.next();

				distance = Haversine.HaverSineDistance(new Pair<AbstractNode>(portNode, candidate));

				if (distance < shortestDistance){
					closest = candidate;
					shortestDistance = distance;
				}
			}
			Pair<AbstractNode> endpoints= new Pair<AbstractNode>(portNode, closest);
			MaritimeEdge edge = new MaritimeEdge(endpoints,0.0,0.0,shortestDistance,pmEdgeIndex, "Sea");
			pmEdgeIndex++;
			//TODO: this is where port is added to the network
			this.seaNetwork.addEdge(edge, endpoints);
		}
		System.out.println("port-maritime connections are created");
		
		//parsing new OD relations characteristics
		System.out.println("Parsing new OD relations characteristics");
		ResultSet relation_set= stm.executeQuery("SELECT * FROM Relations_new ORDER BY [iso3_o],[iso3_d] ASC");
		ODrelations= new HashMap<String, HashMap<String,double[]>>();
		while(relation_set.next())
		{
			String from=String.valueOf(relation_set.getString("iso3_o"));
			String to=String.valueOf(relation_set.getString("iso3_d"));
			
			//cost for each mode is added
			double data[] = new double[3];
			double language= Double.valueOf(relation_set.getString("Language"));
			double contig= Double.valueOf(relation_set.getString("Contig"));
			double rta= Double.valueOf(relation_set.getString("rta_adapted"));
			data[0]=language;
			data[1]=contig;
			data[2]=rta;
			
			this.putODrelations(from, to, data);
//			System.out.println("origin, destination, mode, cost: "+fromID+","+toID+","+mode+","+cost);
		}
		relation_set.close();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("relation_set is parsed, time: "+processTime+" ms");	
		
		//parsing the reference time and distance
		//this database is used to compute the utility function of the mode
		//TODO: meaning that whenever the network is changed, the database needs to be updated, 
		//particularly the average distance and average speed values
		System.out.println("Parsing base case OD characteristics");
		//here the centroid array size is +1 to allow arrays based on the IDs
		baseOD = new ODdata ("baseline scenario",centroids.size()+1,modes,commodities.size(),timeHorizon);
//		System.out.println("centroid size, mode, commodity, time horizon: "+centroids.size()+","+modes+","
//		+commodities.size()+","+timeHorizon);
		
//		ResultSet baseSet= stm.executeQuery("SELECT * FROM Model_OD_Cost_base_case ORDER BY [FromCID],[ToCid] ASC");
		
		ResultSet baseSet= stm.executeQuery("SELECT * FROM Model_OD_base_case ORDER BY [FromCID],[ToCid] ASC");
		while(baseSet.next())
		{
			int fromID=(int)Math.round(Double.valueOf(baseSet.getString("FromCID")));
			int toID=(int)Math.round(Double.valueOf(baseSet.getString("ToCID")));
			int mode=(int)Math.round(Double.valueOf(baseSet.getString("Mode")));
			
			//skipping mode sea 2
//			if(mode>=4)
//				continue;
			
			String originISO =(String) centroids.get(fromID).getISOAlpha3();
			String destinationISO =(String) centroids.get(toID).getISOAlpha3();
			
//			Centroid destination =centroids.get(toID);
			double avTime= Double.valueOf(baseSet.getString("AvTime"));
			double avDistance= Double.valueOf(baseSet.getString("AvDistance"));
			
			//cost for each mode is added
//			double cost= Double.valueOf(baseSet.getString("Cost"));
			double language= Double.valueOf(baseSet.getString("Language"));
			double contig= Double.valueOf(baseSet.getString("Contig"));
			double rta= Double.valueOf(baseSet.getString("rta"));
			
			//using new OD relations data
//			double language,contig,rta;
//			if(originISO.equalsIgnoreCase("MNE") || destinationISO.equalsIgnoreCase("MNE"))
//			{
////				System.out.println("missing origin, destination: "+originISO+","+destinationISO);
//				language =0;
//				contig =0;
//				rta =0;
//			}
//			else
//			{
//				language= this.ODrelations.get(originISO).get(destinationISO)[0];
//				contig= this.ODrelations.get(originISO).get(destinationISO)[1];
//				rta= this.ODrelations.get(originISO).get(destinationISO)[2];
//			}
			
//			System.out.println("origin,destination,fromISO,destinationISO, mode, cost: "
//					+fromID+","+toID+","+originISO+","+destinationISO+","
//					+mode+","+cost+","+language+","+contig+","+rta);
			
//			//average distance from cenroid i to centroid j
//			baseOD.putODDistance(fromID,toID,mode,avDistance);			
//			//average travel time from centroid i to centroid j
//			baseOD.putODTime(fromID,toID, mode,avTime);			
////			baseOD.putODCost(fromID, toID, mode, cost);
//			//language: language related coefficient
//			baseOD.putODLanguage(fromID, toID, language);
//			//contig: border related coefficient
//			baseOD.putODContig(fromID, toID, contig);
//			//rta: trade related coefficient
//			baseOD.putODRta(fromID, toID, rta);
		}
		
		baseSet.close();
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("base case OD relations is parsed, time: "+processTime+" ms");	
		
		System.out.println("Parsing OD data");
		//new trade data (ENV)
		ResultSet od_set= stm.executeQuery("SELECT * FROM Trade_centroids_ENV_Complete_Price_Corrected where "
//				+ "year= 2011 or "
//				+ "year=2015 or year =2035" 
//				+ " or year =2030 or year =2050"
//				+ "year =2035"
				+ "year =2011 or year=2015 or year=2020 or year=2025 or year=2030 or year=2035 "
//				+ "or year=2045 or year =2050 "
				+ "ORDER BY [id_centroid_origin],[id_centroid_destination] ASC");

		//old trade data (ECO)
		//ResultSet od_set= stm.executeQuery("SELECT * FROM Trade_centroids_Baseline where year=2015 ORDER BY [C_i],[C_j] ASC");
		
		//Intraregional trade
//		ResultSet od_set= stm.executeQuery("SELECT * FROM Trade_centroids_IntraRegionalTrade_Complete where "
//				+ "year =2015 or year=2020 or year =2025 or year= 2030 or year =2035 "
//				+ "ORDER BY [id_centroid_origin],[id_centroid_destination] ASC");

		while(od_set.next())
		{
			//parsing the id
			//TODO: check the consistency of the node ID
			int c_i=Integer.valueOf(od_set.getString("id_centroid_origin"));
			//int c_i=Integer.valueOf(od_set.getString("C_i"))-1;
			int c_j=Integer.valueOf(od_set.getString("id_centroid_destination"));
			//int c_j=Integer.valueOf(od_set.getString("C_j"))-1;
			//parsing the year index
			int year=(Integer.valueOf(od_set.getString("year"))-2010)/5;		
			
			//for each of the commodity
			for(int commodity=0; commodity<25;commodity++)
			{
				//assign trade value  or weight values from centroid i to centroid j in year (year) for each commodity j					
				double tradeFlow=0.001*Double.valueOf(od_set.getString(commodities.get(commodity).getName()));	
				baseOD.setOd_Values(c_i,c_j, commodity, year, tradeFlow);
//				System.out.println("origin: "+c_i+ ", destination: "+c_j+", year: "+year+
//						" commodity: "+commodities.get(commodity).getName()+", value:"+od_set.getString(commodities.get(commodity).getName()));
			}	
		}
		od_set.close();
		
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("OD Flows are parsed");
		System.out.println("network components are parsed, time: "+processTime+" ms");
	}

	//cost-based mode share
	private void buildModeShare(Statement stm) throws SQLException {
		modeShareCost = new ModeShareCost();
		ResultSet modeShareSet=stm.executeQuery("SELECT * FROM mode_choice_cost ORDER BY [Code] ASC");
		while(modeShareSet.next())
		{
			String code=String.valueOf(modeShareSet.getString("Code"));
			double value=Double.valueOf(modeShareSet.getString("Value"));

			//parsing mode share value, only looking for mode share by air, rail, road and sea	
			//asc:alternative specific coefficient for each mode of transport
			//at the moment asc is set to zero
			if(code.equals("ASC_A"))
				modeShareCost.getAsc()[0]=value;
			else if(code.equals("ASC_RL"))
				modeShareCost.getAsc()[1]=value;
			else if(code.equals("ASC_R"))
				modeShareCost.getAsc()[2]=value;

			//waterways asc is similar to sea +0.5
			//+0.5 value was for experiment
			else if(code.equals("ASC_S"))
			{
				//asc for sea
				modeShareCost.getAsc()[3]=value;
//				modeShareCost.getAsc()[4]=value;
//				modeShareCost.getAsc()[5]=value+0.5;
			}

			//parsing coniguity coefficient for each mode of transport, only for road and rail
//			else if(code.equals("CONTRD"))
//				//coefficient for road contig 0 
//				modeShareCost.getContig()[0]=value;
//			else if(code.equals("CONTRL"))
//				//coefficient for rail contig
//				modeShareCost.getContig()[1]=value;
//			
//			//parsing cost coefficient for each type of cargo
//			else if(code.equals("COST_CN"))
//			{
//				//coefficient for container cargo 
//				modeShareCost.getCost_cargo()[0]=value;
//				
//			}
//			else if(code.equals("COST_DB"))
//			{
//				//coefficient for dry bulk cargo
//				modeShareCost.getCost_cargo()[1]=value;
//				
//			}
//			//coefficient for liquid bulk
//			else if(code.equals("COST_LB"))
//				modeShareCost.getCost_cargo()[2]=value;
//			//coefficient for general cargo
//			else if(code.equals("COST_GC"))
//				modeShareCost.getCost_cargo()[3]=value;
//			//coefficient for roll on roll off
//			else if(code.equals("COST_RR"))
//				modeShareCost.getCost_cargo()[4]=value;
//			
//			//coefficient for trade agreement
//			else if(code.equals("REGTRD"))
//				//coefficient for rail distance
//				modeShareCost.setRta(value);
//			else 
//			{	//time coefficient for each commodity 
//				int ID=Integer.valueOf(code);
//				modeShareCost.getTime()[ID]=value;
//			}
		}
		modeShareSet.close();
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
//						System.out.println("comName, comID:"+comName+","+comID+","+carbonIntensity[c][i-3]);
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
		
	private void parsePortHinterlandFile() throws SQLException {
		try {
			CSVReader reader = new CSVReader(new FileReader(port_hinterland_dir), ',','#', 1);
			String[] nextLine;
			int hinterlandIndex=1;
			while ((nextLine = reader.readNext()) != null) {
				
				int centroidID =Integer.valueOf(nextLine[0]);
				int portID   = Integer.valueOf(nextLine[3]);
				String country = String.valueOf(nextLine[1]);
				double distance=Double.valueOf(nextLine[7]);
				double time = Double.valueOf(nextLine[6]);// in hour
				double capacity = Double.valueOf(nextLine[5]);
				int rank = Integer.valueOf(nextLine[8]);
				double roadKM = Double.valueOf(nextLine[9]);
				double railKM = Double.valueOf(nextLine[10]);

				//condition to limit the number of ports included in the network
				Centroid centroid = centroids.get(centroidID);
				//here only ports that are part of the hinterland that are included in the hinterland connections
				PortNode port = ports.get(portID);

				Pair<AbstractNode> endpoints= new Pair<AbstractNode>(centroid,port);
				HinterlandEdge edge = new HinterlandEdge (endpoints, "HinterlandTransport", distance, time);
				//setting the id of the hinterland edge based on the hinterland index, so that it can be used to 
				//port hinterland  links
				edge.setRoadKM(roadKM);
				edge.setRailKM(railKM);
				edge.setId(hinterlandIndex);
				centroid.addHinterlandConnection(port, edge);

				//port hinterland links are stored independently but not integrated to the network 
				//they are taken for visualization later
				this.portHinterlandLinks.put(hinterlandIndex,edge);
				hinterlandIndex++;
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parsePortFile() {
		
		realPorts = new HashMap<Integer,PortNode>();
		try {
			CSVReader reader = new CSVReader(new FileReader(port_dir), ',','#', 1);
			String[] nextLine;
			
			while ((nextLine = reader.readNext()) != null) {
				int solutionID   = Integer.valueOf(nextLine[0]);
				int portNodeID = Integer.valueOf(nextLine[2]);
				String portName = String.valueOf(nextLine[1]);
				double handlingCost = Double.valueOf(nextLine[4]);
				double observedThroughput = Double.valueOf(nextLine[9]);
				int seaAreaCode = Integer.valueOf(nextLine[22]);
				
				PortNode port = ports.get(portNodeID);
//				port.setName(portName);
//				port.setSeaAreaCode(seaAreaCode);
				port.setHandlingCosts(handlingCost);
				port.setObservedThroughput(observedThroughput);
				
//				System.out.println("solution id, port node id,  port name, port id_check, port_check, handling cost, observed throughput:"
//						+solutionID+","+portNodeID+","+portName+","+port.getId()+","
//						+port.getLabel()+","+handlingCost+","+observedThroughput);
				
				this.seaNetwork.addVertex(port);
				//adding ports that are used in the model into a special data structure with a new id
				realPorts.put(solutionID,port);
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void constructShortestPath() {
		System.out.println("Building shortest path engine");
		//setting up shortest paths for each mode
		
		Transformer<AbstractEdge, Double> seaEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			//the general cost function used to find the shortest path needs to be defined here
			//TODO: the cost function still needs to be generalized as this is only for SEA
			//so that it is commodity dependent
			//it is better to calculate the cost based on edge rather than node
			public Double transform(AbstractEdge edge)
			{
//				RoadEdge link =(RoadEdge)edge;
				AbstractNode first=edge.getEndpoints().getFirst();
				AbstractNode second= edge.getEndpoints().getSecond();
				
				double edgeCost = edge.getCosts();
//				double edgeCost=link.getTripTime()+link.getPen_sea()+second.getHandlingTime()[0]
//						+second.getCost()[0]+second.getTranshipmentTime();
			
				return edgeCost;
			}
		};
		
		Transformer<AbstractEdge, Double> railEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			//the general cost function used to find the shortest path needs to be defined here
			//TODO: the cost function still needs to be generalized as this is only for SEA
			//so that it is commodity dependent
			//it is better to calculate the cost based on edge rather than node
			public Double transform(AbstractEdge edge)
			{
				RoadEdge link =(RoadEdge)edge;
				AbstractNode first=edge.getEndpoints().getFirst();
				AbstractNode second= edge.getEndpoints().getSecond();
//				double edgeCost=link.getTripTime()+link.getPen_rail();
//				double edgeCost=(link.getTripTime()+link.getPen_rail())*50+link.getRoadRealDistance()*0.025;
				double edgeCost=link.getRoadRealDistance();
//				double edgeCost=link.getRoadRealDistance()+link.getPen_rail();
				return edgeCost;
			}
		};
		
		Transformer<AbstractEdge, Double> roadEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			//the general cost function used to find the shortest path needs to be defined here
			//TODO: the cost function still needs to be generalized as this is only for SEA
			//so that it is commodity dependent
			//it is better to calculate the cost based on edge rather than node
			public Double transform(AbstractEdge edge)
			{
				RoadEdge link =(RoadEdge)edge;
				AbstractNode first=edge.getEndpoints().getFirst();
				AbstractNode second= edge.getEndpoints().getSecond();
				double edgeCost=link.getTripTime();
//				double edgeCost = link.getRoadRealDistance();
//						+second.getCost()[0];
				return edgeCost;
			}
		};
		
		
		Transformer<AbstractEdge, Double> airEdgeCost = new Transformer<AbstractEdge, Double>()
		{
			public Double transform(AbstractEdge edge)
			{
				RoadEdge link =(RoadEdge)edge;
				AbstractNode first=edge.getEndpoints().getFirst();
				AbstractNode second= edge.getEndpoints().getSecond();
				double edgeCost=link.getTripTime()+second.getHandlingTime()[0]
						+second.getCost()[0];
				return edgeCost;
			}
		};

		this.seaShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.seaNetwork, seaEdgeCost, true);		
		this.airShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.airNetwork, airEdgeCost, true);	
		this.roadShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.roadNetwork, roadEdgeCost, true);
		this.railShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.railNetwork, railEdgeCost, true);
//		this.waterwayShortestPath = new DijkstraShortestPath<AbstractNode, AbstractEdge>(this.waterwayNetwork, nev, true);
	}

	private double getValueofQuery(Statement stm, String query) throws SQLException {
		ResultSet rs=stm.executeQuery(query);
		double value = 0;
		//taking the result of the first column
		rs.next();
		value=rs.getDouble(1);
//		System.out.println("query is:"+query+" value of query: "+value);	
		rs.close();
		return value;
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

	public ValueToWeight getValToWeight() {
		return valToWeight;
	}

	public void setValToWeight(ValueToWeight valToWeight) {
		this.valToWeight = valToWeight;
	}

	public ModeShare getModeShare() {
		return modeShare;
	}
	
	public ModeShareCost getModeShareCost() {
		return modeShareCost;
	}

	public void setModeShare(ModeShare modeShare) {
		this.modeShare = modeShare;
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

	public HashMap<Integer, AirportNode> getAirports() {
		return airports;
	}

	public void setAirports(HashMap<Integer, AirportNode> airports) {
		this.airports = airports;
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
		if (this.railShortestPath.getPath(source, target)==null)
		{
			System.out.println("there is no path from: "+source.getName()
			+" to: "+target.getName());
			return new ArrayList<AbstractEdge>();
		}
		else
		return this.railShortestPath.getPath(source, target);
	}
	
	public List<AbstractEdge> getPathAir(Centroid source, Centroid target){

		
		if (this.airShortestPath.getPath(source, target)==null)
		{
			System.out.println("there is no path from: "+source.getName()
			+" to: "+target.getName());
			return new ArrayList<AbstractEdge>();
		}
		else
			return this.airShortestPath.getPath(source, target);
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
	
	public UndirectedSparseGraph<AbstractNode, AbstractEdge> getAirNetworkGraph()
	{
		return this.airNetwork;
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
	
	public void resetAirNetworkGraph()
	{
		Collection<AbstractEdge> edges =this.airNetwork.getEdges();
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

	public HashMap<Integer, PortNode> getRealPorts() {
		return realPorts;
	}

	public HashMap<Integer, String> getEdges_country() {
		return edges_country;
	}

	public void setEdges_country(HashMap<Integer, String> edges_country) {
		this.edges_country = edges_country;
	}

	public HashMap<Integer, String> getEdges_region() {
		return edges_region;
	}

	public double[] getOilPrices() {
		return oilPrices;
	}
	
	public double[] getGasPrices() {
		return gasPrices;
	}
	
	public double[][] getCarbonIntensity() {
		return carbonIntensity;
	}
	
	public void putODrelations(String origin, String destination, double[] data){
		HashMap<String, double[]> destinationData;
		if(this.ODrelations.containsKey(origin)){
			destinationData = this.ODrelations.get(origin);
		}else{
			destinationData = new HashMap<String, double[]>();
		}
		destinationData.put(destination, data);
		this.ODrelations.put(origin, destinationData);	
	}
}//end of class
