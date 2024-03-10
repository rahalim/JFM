package jfm.gui;



import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import au.com.bytecode.opencsv.CSVReader;
import processing.core.PApplet;
import processing.core.PFont;
import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MaritimeEdge;
import jfm.network.MaritimeNode;
import jfm.network.MultiModalNetwork;
import jfm.network.PortNode;
import jfm.gui.markers.DepotMarker;
import jfm.gui.markers.LabeledMarker;
import jfm.gui.markers.NodeMarker;
import jfm.gui.markers.PortMarker;
import jfm.gui.markers.ServiceEdgeMarker;
import de.fhpotsdam.unfolding.UnfoldingMap;
import de.fhpotsdam.unfolding.data.Feature;
import de.fhpotsdam.unfolding.data.GeoJSONReader;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.Marker;
import de.fhpotsdam.unfolding.providers.CartoDB;
import de.fhpotsdam.unfolding.providers.EsriProvider;
import de.fhpotsdam.unfolding.providers.GeoMapApp;
import de.fhpotsdam.unfolding.providers.Google;
import de.fhpotsdam.unfolding.providers.ImmoScout;
import de.fhpotsdam.unfolding.providers.MBTilesMapProvider;
import de.fhpotsdam.unfolding.providers.MapBox;
import de.fhpotsdam.unfolding.providers.OpenStreetMap;
import de.fhpotsdam.unfolding.providers.Yahoo;
import de.fhpotsdam.unfolding.utils.MapUtils;
import de.fhpotsdam.unfolding.utils.ScreenPosition;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;


/**
 * Simple marker display, without the use of MarkerManager.
 * 
 * Conversion between geo-location and screen position is done via the marker, but drawing the markers is done by this
 * application itself. This is the easiest way of drawing own styled markers.
 */
@SuppressWarnings("serial")
public class EurasiaMap extends PApplet {

	private UnfoldingMap map;
	
	private String  db_file_name_prefix=
			"jdbc:sqlserver://SQL-02;"
					+"instanceName=FREIGHT_INSTANCE;"
					+"databaseName=Freight_Network_Model_2015;"
					+"integratedSecurity=true;";
	
	//using local tiles
	public static final String TILES_LOCATION_APPLICATION = "./bin/tiles/Outlook_World_Map.mbtiles";
	public static final String TILES_LOCATION_APPLET = "jdbc:sqlite:data/tiles/Outlook_World_Map.mbtiles";
	public static String mbTilesString = TILES_LOCATION_APPLET;
	
	private Statement stm;	
//	private double scale =5.5E7;
//	private double scale =4.5E7;
	
	//scale for road network
//	private double scale =0.75E6;
	private double scale =0.5E6;
//	private double scale =8E3;
	private double scaleGap;
	
	//attributes of the edges for parsing via sql
	private HashMap <Integer,MaritimeEdge> maritimeEdges;
	private HashMap <Integer, MaritimeNode> maritimeNodes;

	//parsing directly after simulation run
	private	UndirectedSparseGraph<AbstractNode, AbstractEdge> maritimeGraph;
	
	private MultiModalNetwork network;
	
	List<Marker> countryMarkers;
	
	private PFont font;
	private List<Marker> LabeledMarkers = new ArrayList<Marker>();

			
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
	
	//retrieving output files from the database or csv file
	@SuppressWarnings("static-access")
	public EurasiaMap(String ptpEdgesFile, String portThroughputFile, boolean SQLdatabase)
	{	
		if(SQLdatabase==true)
		{
			try {
				connectToDatabase(db_file_name_prefix);
				parseFreightNetwork();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		//for now parse csv-file only for maritime transport
		else
		{
			System.out.println("parsing from csv file");
			parseOutput(ptpEdgesFile,portThroughputFile);
		}
	}
	
	//alternative constructor when it's used directly after model run
	//the visualization depends on mode to limit the memory use
	@SuppressWarnings("static-access")
	public EurasiaMap(MultiModalNetwork network, int mode)
	{		
		this.network= network;
		
		if(mode==1)
		{
			this.maritimeGraph= network.getRailNetworkGraph();
		}
		
		if(mode==2)
		{
			this.maritimeGraph= network.getRoadNetworkGraph();
		}
		
		if(mode==3|| mode==4)
		{
			this.maritimeGraph= network.getSeaNetworkGraph();
		}
	}
	
	public void connectToDatabase(String db_file_name_prefix) throws Exception
	{
		System.out.println("connecting to sql database");
		Connection conn = DriverManager.getConnection
				(db_file_name_prefix);
		stm= conn.createStatement();
		System.out.println("connection established!");
	}
	
	
	public void parseFreightNetwork() throws SQLException
	{	
		maritimeGraph= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		int nodeID=0;
		maritimeEdges=new HashMap<Integer,MaritimeEdge>();
		maritimeNodes= new HashMap<Integer,MaritimeNode>();
		
		//year of result
		int year =2015;
		String mode = "Road";
		String mode2= "Ports_Road_Conn";
		scale=6.5E7;
		
		//queriying data from sql
		ResultSet edge_set=stm.executeQuery
	   ("SELECT [EdgeId],[mode],[Geometry_XLO],[Geometry_YLO],"
	   +"[Geometry_XHI],[Geometry_YHI],[distance],"
	   +"sum([Weight_bulk]+[Weight_container]"
       +"+[Weight_General_Cargo]"
       +"+[Weight_tanker]"
       +"+[Weight_RoRo]) as 'total_weight'"
       +"FROM [Outputs_freight_experiment].[dbo].[Edges_output_2015_with_node_coord]"
//       +"where year="+year+"and mode='Road'"
       +"where year="+year+"and (mode='"+mode+"'"+" or mode='"+mode2+"')"
       +"group by edgeId,mode,[Geometry_XLO],[Geometry_YLO],[Geometry_XHI],[Geometry_YHI],[distance]");
		
		while(edge_set.next())
		{
			int edgeID=Integer.valueOf(edge_set.getString("EdgeID"));	
			String edgeMode = edge_set.getString("mode");
			double firstNodeLat=Double.valueOf(edge_set.getString("Geometry_YLO"));
			double firstNodeLon=Double.valueOf(edge_set.getString("Geometry_XLO"));
			
			//first maritimeNode
			MaritimeNode firstNode = new MaritimeNode(firstNodeLat,firstNodeLon,"maritimeNode"+edgeID,nodeID);
			nodeID++;
			
			double secondNodeLat=Double.valueOf(edge_set.getString("Geometry_YHI"));
			double secondNodeLon=Double.valueOf(edge_set.getString("Geometry_XHI"));
			//second maritimeNode
			MaritimeNode secondNode = new MaritimeNode(secondNodeLat,secondNodeLon,"maritimeNode"+edgeID,nodeID);

			double flow = Double.valueOf(edge_set.getString("total_weight"));			
			double distance = Double.valueOf(edge_set.getString("distance"));
			Pair<AbstractNode> maritimeNodes = new Pair<AbstractNode>(firstNode,secondNode);

//			if(flow>300000)
//			{
				//maritime edge
				MaritimeEdge edge = new MaritimeEdge(maritimeNodes,0,0,distance,0,"Sea");
				edge.addAssignedFlow(flow);
				edge.setMode(edgeMode);
				this.maritimeGraph.addEdge(edge, edge.getEndpoints());
//			}
			//System.out.println("ID: "+edgeID+" lat: "+lat+" lon: "+longf +" flow:"+flow );
		}
		edge_set.close();		
	}   
	
	public void parseOutput(String edgesFile, String portFile)
	{
		
		maritimeGraph= new UndirectedSparseGraph<AbstractNode, AbstractEdge>();
		int nodeID=0;
		maritimeEdges=new HashMap<Integer,MaritimeEdge>();
		maritimeNodes= new HashMap<Integer,MaritimeNode>();
		
		try{
			System.out.println("reading Output file");
			CSVReader reader = new CSVReader(new FileReader(edgesFile), ',','\'', 2);
			String[] nextLine;

			while ((nextLine = reader.readNext()) != null){
				int edgeID=Integer.parseInt(nextLine[0]);
				double firstNodeLat=Double.parseDouble(nextLine[5]);
				double firstNodeLon=Double.parseDouble(nextLine[6]);
				int fromNodeId=Integer.parseInt(nextLine[2]);
				int toNodeId=Integer.parseInt(nextLine[3]);
				//first maritimeNode
				MaritimeNode secondNode 
				= new MaritimeNode(firstNodeLat,firstNodeLon,"maritimeNode"+edgeID,toNodeId);
				
				nodeID++;
				
				double secondNodeLat=Double.parseDouble(nextLine[7]);
				double secondNodeLon=Double.parseDouble(nextLine[8]);
				//second maritimeNode
				MaritimeNode firstNode 
				= new MaritimeNode(secondNodeLat,secondNodeLon,"maritimeNode"+edgeID,fromNodeId);

				String mode =nextLine[4];
				double flow = Double.parseDouble(nextLine[10]);		
				double time = Double.parseDouble(nextLine[9]);
				Pair<AbstractNode> maritimeNodes = new Pair<AbstractNode>(firstNode,secondNode);

				//maritime edge
				MaritimeEdge edge = new MaritimeEdge(maritimeNodes,0,0,time,0,"Sea");
				edge.addAssignedFlow(flow);
				edge.setMode(mode);
				this.maritimeGraph.addEdge(edge, edge.getEndpoints());
//					
//				System.out.println("ID: "+edgeID+" lat: "+firstNodeLat+
//						" lon: "+firstNodeLon +" flow:"+flow );
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	public void setup()
	{
		System.out.println("setting up map");
//		size(1500,800, P3D);
		
		//original size
		size(1700,1000, JAVA2D);
		
		//mac friendly resolution
		//size(1200, 800);
		smooth();
		//map based on local tiles
//				map = new UnfoldingMap(this, new MBTilesMapProvider(mbTilesString));
		
		//default map
//		map = new UnfoldingMap(this, new CartoDB.Positron());
//				map = new UnfoldingMap(this, new CartoDB.DarkMatter());
		map = new UnfoldingMap(this, new EsriProvider.OceanBasemap());
		//		map = new UnfoldingMap (this, new OpenStreetMap.OpenStreetMapProvider());	
		//		map = new UnfoldingMap (this, new Google.GoogleSimplifiedProvider());
		//the custom made map
//				map = new UnfoldingMap(this, new MapBox.ControlRoomProvider());
		//		map = new UnfoldingMap(this, new MapBox.WorldLightProvider());

		map.setTweening(true);
//		
//		map.zoomToLevel(4.82f);
//		
		map.zoomToLevel(3.9f);
//		
//		//world view
////		map.panTo(new Location(15.5f, 13.5f));
//		
//		//China to CA view
//		map.panTo(new Location(37.57f, 92.21f));
//		
//		//eurasian view
		map.panTo(new Location(46.92f, 64f));
	
		//adding kazakhztan border
//		List<Feature> countries = GeoJSONReader.loadData(this,"KAZ_adm0.geojson");
//		countryMarkers = MapUtils.createSimpleMarkers(countries);
//		map.addMarkers(countryMarkers);
//		shadeCountries();
		
		MapUtils.createDefaultEventDispatcher(this, map);
		PFont font = createFont("serif-bold", 12);		
		textFont(font);
		
		drawMaritimeNetwork();
		background(255,255,255);
	}	

	public void draw() {
		map.draw();
		PFont font = createFont("serif-bold", 12);	
		
		float scale1=((float) scaleGap/0.5E6f);
		float scale2=scale1*2;
		float scale3=scale1*3;
		float scale4=scale1*4;
		float scale5=scale1*5;
		float scale6=scale1*6;
		
		int scale1Round =(int) Math.ceil(scale1/2);
		int scale2Round =(int) Math.ceil(scale2/2);
		int scale3Round =(int) Math.ceil(scale3/2);
		int scale4Round =(int) Math.ceil(scale4/2);
		int scale5Round =(int) Math.ceil(scale5/2);
		int scale6Round =(int) Math.ceil(scale6/2);
		
		
		String scaleText1= "0 - "+scale1Round+" MTonne";
		String scaleText2= scale1Round+" - "+scale2Round+" MTonne"; 
		String scaleText3= scale2Round+" - "+scale3Round+" MTonne";
		String scaleText4= scale3Round+" - "+scale4Round+" MTonne";
		String scaleText5= scale4Round+" - "+scale5Round+" MTonne";
		String scaleText6= scale5Round+" - "+scale6Round+" MTonne";
			
//		System.out.println("scale 1 and scale 5: "+scale1+", "+scale5);
		
		//legend box
		int xlegend=1370;
		int ylegend=830;
		this.g.fill(255,255,255);
		this.g.rect(xlegend, ylegend,240,120);
		
		//first legend line
		this.g.stroke(0,0,0);
		this.g.strokeWeight(scale1);
		this.g.line(xlegend+10, ylegend+10, xlegend+50,ylegend+10);
		
		//first legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText1, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+15));
		
		//second line
		this.g.strokeWeight(scale2);
		this.g.line(xlegend+10, ylegend+30, xlegend+50,ylegend+30);
		
		//second legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText2, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+35));
		
		//third line
		this.g.strokeWeight(scale3);
		this.g.line(xlegend+10, ylegend+50, xlegend+50,ylegend+50);

		//third legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText3, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+55));

		//fourth line
		this.g.strokeWeight(scale4);
		this.g.line(xlegend+10, ylegend+70, xlegend+50,ylegend+70);

		//fourth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText4, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+75));

		//fifth line
		this.g.strokeWeight(scale5);
		this.g.line(xlegend+10, ylegend+90, xlegend+50,ylegend+90);

		//fifth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText5, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+95));
		
		//sixth line
		this.g.strokeWeight(scale6);
		this.g.line(xlegend+10, ylegend+110, xlegend+50,ylegend+110);

		//sixth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText6, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+115));
	}

	public void drawMaritimeNetwork()
	{
		System.out.println("drawing assigned flows on Maritime Network"); 

		List<Marker> ServiceEdgeMarkers = new ArrayList<Marker>();
		List<Marker> NodeMarkers = new ArrayList<Marker>();
		List<Marker> LinkMarkers = new ArrayList<Marker>();
		List<LabeledMarker> NodeIDMarkers = new ArrayList<LabeledMarker>();
	
		//loop through the nodes
		Collection<AbstractNode> n=this.maritimeGraph.getVertices();

		for (AbstractNode an : n) {
			//System.out.println("node "+n.iterator().toString()+" lat: " +an.getLat()+" lon: "+an.getLon());
			
			if(an instanceof Centroid)
			{
				Centroid zone = (Centroid)an;
//				if(((Centroid) an).getName().equalsIgnoreCase("Bishkek"))
//					continue;
					
//				if(((Centroid) an).getISOAlpha3().equalsIgnoreCase("TJK") 
//						||((Centroid) an).getISOAlpha3().equalsIgnoreCase("KAZ") 
//						||((Centroid) an).getISOAlpha3().equalsIgnoreCase("TKM")
//						||((Centroid) an).getISOAlpha3().equalsIgnoreCase("KGZ")
//						||((Centroid) an).getISOAlpha3().equalsIgnoreCase("UZB")
//						||((Centroid) an).getISOAlpha3().equalsIgnoreCase("MNG"))
//				{
//					NodeMarker city = new NodeMarker(new Location(an.getLat(),an.getLon()), 5);
//					int r=150, g=130, b=180;
//					city.setNodeColor(r,g,b);
//					city.setStroke(0,0,128);
//					city.setFill(true);
//
//					NodeMarkers.add(city);
//
//					//drawing label for the nodes
//					font = loadFont("Helvetica-16.vlw");	
//					//				LabeledMarker nodeIDMarker = new LabeledMarker 
//					//						(new Location(an.getLat(),an.getLon()),
//					//						an.getName()+" id: "+an.getId()+"_"+((Centroid)an).getISOAlpha3(),font, 5);		
//
//					LabeledMarker nodeIDMarker = new LabeledMarker (new Location(an.getLat(),an.getLon()), an.getName()+"_"
//							+((Centroid)an).getISOAlpha3(),font, 2);
//					LabeledMarkers.add(nodeIDMarker);
//				}
				
				if(
						zone.getRegion().equalsIgnoreCase("China")
						||zone.getRegion().equalsIgnoreCase("South Korea") 
						||zone.getRegion().equalsIgnoreCase("Russia")
						||zone.getRegion().equalsIgnoreCase("Euro Area")
						//						||zone.getISOAlpha3().equalsIgnoreCase("TJK")
						//						||zone.getISOAlpha3().equalsIgnoreCase("KAZ")
						//						||zone.getISOAlpha3().equalsIgnoreCase("KGZ")
						//						||zone.getISOAlpha3().equalsIgnoreCase("TKM")
						//						||zone.getISOAlpha3().equalsIgnoreCase("UZB")
						//						||zone.getISOAlpha3().equalsIgnoreCase("MNG")
						)
				{

					NodeMarker city = new NodeMarker(new Location(an.getLat(),an.getLon()), 5);
					int r=150, g=130, b=180;
					city.setNodeColor(r,g,b);
					city.setStroke(0,0,128);
					city.setFill(true);

					NodeMarkers.add(city);

					//drawing label for the nodes
					font = loadFont("Helvetica-16.vlw");	
					LabeledMarker nodeIDMarker = new LabeledMarker (new Location(an.getLat(),an.getLon()), an.getName()+"_"
							+((Centroid)an).getISOAlpha3(),font, 2);
					LabeledMarkers.add(nodeIDMarker);
				}
			}
			
//			if(an instanceof PortNode)
//			{
//				PortMarker port = new PortMarker(new Location(an.getLat(),an.getLon()), 1);
//				port.setRectSize(6, 6);
//				port.setNodeColor(0, 0, 128);
//				port.setStrokeWeight(5);
////				NodeMarkers.add(port);
//				
//				int throughput = (int) (((PortNode) an).getThroughput()/1.5E5);
//				NodeMarker p = new NodeMarker(new Location(an.getLat(),an.getLon()), throughput);
//				int r=70, g=130, b=180;
//				p.setNodeColor(r,g,b);
//				p.setStroke(0,0,128);
//				p.setFill(true);
////				NodeMarkers.add(p);
//				
//				font = loadFont("Helvetica-16.vlw");	
//				LabeledMarker portIDMarker = new LabeledMarker (new Location(an.getLat(),an.getLon()),
//						an.getName()+" id: "+an.getId()+" throughput: "+Math.round(((PortNode)an).getThroughput()),font, 5);		
//				LabeledMarkers.add(portIDMarker);
//			}			
		}//end of loop	

		//adding markers for ports
//		map.addMarkers(LabeledMarkers);
		//adding markers for nodes
//		map.addMarkers(NodeMarkers);
			
		//drawing hinterland links
//		Collection<HinterlandEdge> hEdges = this.network.getPortHinterlandLinks().values();
//		for (HinterlandEdge hinterEdge : hEdges) {
//			Double lineWidth = hinterEdge.getAssignedflow()/4.5E6;
//			
////			System.out.println("assigned flow is: "+lineWidth);
//			Pair <AbstractNode> endPoints = hinterEdge.getEndpoints();
//			if(lineWidth<=0)
//			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
//				lineWidth=2.0;
//			}
//			else if (lineWidth>0 && lineWidth<=1)
//			{
//				//					System.out.println("negative or zero flow is detected :"+lineWidth);
//				lineWidth=1.0;
//			}
//
//			float mapLineWidth= lineWidth.floatValue();
//
//			int r=0;
//			int g=0; 
//			int b=0;
//			drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);				
//		}

		System.out.println("drawing edges"); 
		//loop through the edges of the maritimeGraph
		//drawing the original maritime network
		Collection<AbstractEdge> edges=this.maritimeGraph.getEdges();
		Double lineWidth=0.0;
		double maxFlow=0.0;
		for (AbstractEdge abstractEdge : edges) {
			Pair <AbstractNode> endPoints = abstractEdge.getEndpoints();
			lineWidth = (abstractEdge.getAssignedflow()/scale);
			
			if(maxFlow<abstractEdge.getAssignedflow())
				maxFlow=abstractEdge.getAssignedflow();
			
//			Double travel = abstractEdge.getLength();
			
			//loop to create labels for each link
//			AbstractNode firstNode =endPoints.getFirst();
//			AbstractNode secondNode =endPoints.getSecond();
//			font=loadFont("Helvetica-16.vlw");
////			System.out.println("printing flows from,to: "+firstNode.getId()+","+secondNode.getId()+
////					","+lineWidth);
//			
//			//adding link marker
//			LabeledMarker linkMarker =new LabeledMarker 
//					(new Location(
//							firstNode.getLat()+((secondNode.getLat()-firstNode.getLat())/2),
//							firstNode.getLon()+((secondNode.getLon()-firstNode.getLon())/2)),
//									String.valueOf(Math.round(lineWidth))
//											,font, 1);	
//			LinkMarkers.add(linkMarker);
			
			//to take out unassigned links
			if(lineWidth<=0)
			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
				lineWidth=1.0;
				continue;
			}
			else if (lineWidth>0 && lineWidth<=1)
			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
				lineWidth=3.0;
			}

			float mapLineWidth= lineWidth.floatValue();
			
//			System.out.println("map width:"+mapLineWidth);
			
			//blue volume
//			int r=80, g=80, b=224;
//			int r=72,g=209,b=204;
//			int r=244,g=208,b=63;
//			int r=52,g=152,b=219;
			
			if (abstractEdge instanceof MaritimeEdge)
			{
				int r=113, g=113, b=141;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
			
			if (abstractEdge.getMode().equalsIgnoreCase("Road"))
			{
//				int r=113, g=113, b=141;
				
				//blue
				int r=80, g=80, b=224;
//				int r=72,g=209,b=204;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
			
			if (abstractEdge.getMode().equalsIgnoreCase("Rail"))
			{
				int r=0, g=0, b=0;
				//blue
//				int r=80, g=80, b=224;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
			
			//for anything else like connector and interface links
//			else
//			{
////				int r=52,g=152,b=219;
//				int r=204,g=102,b=0;
//				drawServiceEdges(ServiceEdgeMarkers, an, mapLineWidth, r, g, b);
//			}
		}// end of loop
		System.out.println("adding markers, max flow:"+maxFlow); 
		this.scaleGap=maxFlow/6;
			
		map.addMarkers(ServiceEdgeMarkers);
	
		//if we want to display volume label in each link
//		map.addMarkers(LinkMarkers);
		
//		adding markers for ports
		map.addMarkers(LabeledMarkers);
//		adding markers for nodes
		map.addMarkers(NodeMarkers);
		System.out.println("drawing complete");
	}

	void pieChart(float diameter, int[] data, ScreenPosition position) {
		float lastAngle = 0;
		for (int i = 0; i < data.length; i++) {
			float gray = map(i, 0, data.length, 150, 255);
			fill(gray);
			//System.out.println(" drawing pie chart "+data[i]+" the diameter is "+diameter+" the position is "+position.x);
			arc(position.x, position.y, diameter, diameter, lastAngle, lastAngle+radians(data[i]));
			lastAngle += radians(data[i]);
		}
	}
	
	public void mouseMoved() {
		// Simplest method to check for hit test.
		for (Marker portMarker : LabeledMarkers) {
			if (portMarker.isInside(map, mouseX, mouseY)) {
				portMarker.setSelected(true);
			} else {
				portMarker.setSelected(false);
			}	
		}
	}

	/**
	 * @param EdgeMarkers
	 * @param an
	 * @param mapLineWidth
	 * @param r (red)
	 * @param g (green)
	 * @param b (blue)
	 */
	private void drawServiceEdges(List<Marker> ServiceEdgeMarkers, Pair<AbstractNode> an, float mapLineWidth,
			int r, int g,int b) {
		
		double xs=an.getFirst().getLon();
		double ys=an.getFirst().getLat();
		double xe=an.getSecond().getLon();
		double ye=an.getSecond().getLat();

		//correcting the rendering of the edges of the physical maritime network
		if ((Math.abs(an.getFirst().getLon()-180)+Math.abs(an.getSecond().getLon()+180)) < 
				(Math.abs(an.getSecond().getLon())+ Math.abs(an.getFirst().getLon()))){

			//				System.out.println(" first condition is accessed");
			double xtmp=180;
			double ytmp=(int)(-(-an.getFirst().getLat()+((-an.getSecond().getLat()+an.getFirst().getLat())/
					(360.+an.getSecond().getLon()-an.getFirst().getLon()))*(180.-an.getFirst().getLon())));

			ServiceEdgeMarker em = new ServiceEdgeMarker(new Location(ys,xs), new Location(ytmp,xtmp));

			// the line width should be drawn based on the flow
			em.setStrokeWidth(mapLineWidth);
			em.setEdgeColor(r,g,b);
//			em.setStrokeColor(r, g, b);
			ServiceEdgeMarkers.add(em);

			xs=-180;
			ys=(int)(-(-an.getSecond().getLat()+((-an.getFirst().getLat()+an.getSecond().getLat())/
					(-360.+an.getFirst().getLon()-an.getSecond().getLon()))*(-180-an.getSecond().getLon())));			
		}

		if ((Math.abs(an.getFirst().getLon()+180)+Math.abs(an.getSecond().getLon()-180)) < 
				(Math.abs(an.getSecond().getLon())+ Math.abs(an.getFirst().getLon()))){

			//				System.out.println(" second condition is accessed");
			double xtmp=180;
			double ytmp=(int)(-(-an.getSecond().getLat()+((-an.getFirst().getLat()+an.getSecond().getLat())/
					(360.+an.getFirst().getLon()-an.getSecond().getLon()))*(180.-an.getSecond().getLon())));

			ServiceEdgeMarker em = new ServiceEdgeMarker(new Location(ytmp,xtmp), new Location(ye,xe));
			em.setStrokeWidth(mapLineWidth);
			em.setEdgeColor(r,g,b);
//			em.setStrokeColor(r, g, b);
			ServiceEdgeMarkers.add(em);

			xe=-180;
			ye=(int)(-(-an.getFirst().getLat()+((-an.getSecond().getLat()+an.getFirst().getLat())/
					(-360.+an.getSecond().getLon()-an.getFirst().getLon()))*(-180.-an.getFirst().getLon())));
		}
		ServiceEdgeMarker em = new ServiceEdgeMarker(new Location(ys,xs),new Location(ye,xe));
		em.setStrokeWidth(mapLineWidth);
		em.setEdgeColor(r,g,b);
//		em.setStrokeColor(r, g, b);
		ServiceEdgeMarkers.add(em);
	}
	
	
	public void shadeCountries() {
		for (Marker marker : countryMarkers) {
			// Find data for country of the current marker
			String countryId = marker.getId();

			// Encode value as brightness (values range: 0-1000)
			float transparency = map(50, 0, 700, 10, 255);
			//setting the color of the marker
			marker.setColor(color(100, 0, 0, transparency));
		} 
	}
	
	public static void main(String[] args) 
	{
		PApplet.main(new String[] { EurasiaMap.class.getName() });
	}
}
	
	
	
