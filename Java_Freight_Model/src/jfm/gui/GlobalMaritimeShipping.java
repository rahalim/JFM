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
import java.util.Set;

import au.com.bytecode.opencsv.CSVReader;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import jfm.network.AbstractEdge;
import jfm.network.AbstractNode;
import jfm.network.Centroid;
import jfm.network.HinterlandEdge;
import jfm.network.MaritimeEdge;
import jfm.network.MaritimeNode;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.network.PortNode;
import jfm.gui.markers.DepotMarker;
import jfm.gui.markers.LabeledMarker;
import jfm.gui.markers.NodeMarker;
import jfm.gui.markers.PortMarker;
import jfm.gui.markers.ServiceEdgeMarker;
import de.fhpotsdam.unfolding.UnfoldingMap;
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
public class GlobalMaritimeShipping extends PApplet {

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
	//scale for maritime transport
//	private double scale =5.5E7;
//	private double scale =10.5E7;
//	private double scale =1E10;
	
	//scale for CO2 emissions
//	private double scale = 2.7E6;
	
	//scale for road network
	private double scale =4.5E6;
	
	//scale for rail network
//	private double scale =7.5E5;
	
	//threshold for flows visualized on the map
	double threshold=0E6;
	
//	private double scale =8E3;
	private double scaleGap;
	
	//attributes of the edges for parsing via sql
	private HashMap <Integer,MaritimeEdge> maritimeEdges;
	private HashMap <Integer, MaritimeNode> maritimeNodes;

	//parsing directly after simulation run
	private	UndirectedSparseGraph<AbstractNode, AbstractEdge> maritimeGraph;
	
	private MultiModalNetworkCostNoSQL network;
	
	private PFont font;
	private List<Marker> LabeledMarkers = new ArrayList<Marker>();
//	private boolean record;
			
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
	public GlobalMaritimeShipping(String ptpEdgesFile, String portThroughputFile, boolean SQLdatabase)
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
	public GlobalMaritimeShipping(MultiModalNetworkCostNoSQL network, int mode)
	{		
		this.network= network;
		if(mode==0)
		{
			this.maritimeGraph= network.getRailNetworkGraph();
		}
		if(mode==1)
		{
			this.maritimeGraph= network.getRoadNetworkGraph();
		}
		
		if(mode==2)
		{
			this.maritimeGraph= network.getSeaNetworkGraph();
		}
	}
	
	public GlobalMaritimeShipping(MultiModalNetworkCostNoSQL network, int mode, double threshold)
	{		
		this.network= network;
		this.threshold=threshold;
		
		if(mode==0)
		{
			this.maritimeGraph= network.getRailNetworkGraph();
		}
		
		if(mode==1)
		{
			this.maritimeGraph= network.getRoadNetworkGraph();
		}
		
		if(mode==2)
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
	
	//only read when visualizing results from output file and not from real time running
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
				//first maritimeNode
				MaritimeNode secondNode 
				= new MaritimeNode(firstNodeLat,firstNodeLon,"maritimeNode"+edgeID,nodeID);
				
				nodeID++;
				
				double secondNodeLat=Double.parseDouble(nextLine[7]);
				double secondNodeLon=Double.parseDouble(nextLine[8]);
				//second maritimeNode
				MaritimeNode firstNode 
				= new MaritimeNode(secondNodeLat,secondNodeLon,"maritimeNode"+edgeID,nodeID);

				String mode =nextLine[4];
				double flow = Double.parseDouble(nextLine[10]);		
				double distance = Double.parseDouble(nextLine[9]);
				Pair<AbstractNode> maritimeNodes = new Pair<AbstractNode>(firstNode,secondNode);

				//maritime edge
				MaritimeEdge edge = new MaritimeEdge(maritimeNodes,0,0,distance,0,"Sea");
				edge.addAssignedFlow(flow);
				edge.setMode(mode);
				this.maritimeGraph.addEdge(edge, edge.getEndpoints());
					
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
		size(1900,1200, JAVA2D);
		
		//mac friendly resolution
//		size(1400, 750);
		smooth();
		//map based on local tiles
//				map = new UnfoldingMap(this, new MBTilesMapProvider(mbTilesString));
		
		//default map
		map = new UnfoldingMap(this, new CartoDB.Positron());
//				map = new UnfoldingMap(this, new CartoDB.DarkMatter());
//				map = new UnfoldingMap(this, new EsriProvider.OceanBasemap());
//				map = new UnfoldingMap (this, new OpenStreetMap.OpenStreetMapProvider());	
//				map = new UnfoldingMap (this, new Google.GoogleSimplifiedProvider());
		
		//the custom made map
//				map = new UnfoldingMap(this, new MapBox.ControlRoomProvider());
//				map = new UnfoldingMap(this, new MapBox.WorldLightProvider());
//				map = new UnfoldingMap(this, new EsriProvider.WorldGrayCanvas());
				
		map.setTweening(true);
		//original
//		map.zoomToLevel(2.6f);
		map.zoomToLevel(8f);
		
		//Java view
		map.panTo(new Location(-7.47f, 110f));
		
		//eurasian view
//		map.panTo(new Location(28f, 50f));
		
		MapUtils.createDefaultEventDispatcher(this, map);
		PFont font = createFont("serif-bold", 12);		
		textFont(font);
		
		drawMaritimeNetwork();
		background(70);
	}	

	public void draw() {
		map.draw();	
		
		//=========================building legend box automatically====================================
		float scale1=((float) scaleGap/(float)this.scale);
		float scale2=scale1*2;
		float scale3=scale1*3;
		float scale4=scale1*4;
		float scale5=scale1*5;
		float scale6=scale1*6;
		
		//scale usually has to be adjusted and this time, 1 line width represents 1E6 CO2 emissions
		int scale1Round =(int) Math.ceil(scale1*this.scale/1E6);
		int scale2Round =(int) Math.ceil(scale2*this.scale/1E6);
		int scale3Round =(int) Math.ceil(scale3*this.scale/1E6);
		int scale4Round =(int) Math.ceil(scale4*this.scale/1E6);
		int scale5Round =(int) Math.ceil(scale5*this.scale/1E6);
		int scale6Round =(int) Math.ceil(scale6*this.scale/1E6);
		
		String scaleText1= "0 - "+scale1Round+" MTonne";
		String scaleText2= scale1Round+" - "+scale2Round+" MTonne"; 
		String scaleText3= scale2Round+" - "+scale3Round+" MTonne";
		String scaleText4= scale3Round+" - "+scale4Round+" MTonne";
		String scaleText5= scale4Round+" - "+scale5Round+" MTonne";
		String scaleText6= scale5Round+" - "+scale6Round+" MTonne";
			
//		System.out.println("scale 1 and scale 5: "+scale1+", "+scale5);
		
		//legend box
		int xlegend=1340;
//		int xlegend=1040;
		
		//original
//		int ylegend=830;
		
		int ylegend=770;
//		int ylegend=510;
		
		this.g.fill(255,255,255);
		this.g.rect(xlegend, ylegend,240,180);
		
		//first legend line
		//int r=119, g=136, b=153;
		
		//blue line for road
		int r=80, g=80, b=224;
		
		//black line for rail
//		int r=0, g=0, b=0;
		
		this.g.stroke(r,g,b);
		this.g.strokeWeight(scale1);
		this.g.line(xlegend+15, ylegend+10, xlegend+50,ylegend+10);
		
		//first legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText1, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+15));
		
		//second line
		this.g.strokeWeight(scale2);
		this.g.line(xlegend+15, ylegend+40, xlegend+50,ylegend+40);
		
		//second legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText2, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+45));
		
		//third line
		this.g.strokeWeight(scale3);
		this.g.line(xlegend+15, ylegend+70, xlegend+50,ylegend+70);

		//third legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText3, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+75));

		//fourth line
		this.g.strokeWeight(scale4);
		this.g.line(xlegend+15, ylegend+100, xlegend+50,ylegend+100);

		//fourth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText4, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+105));

		//fifth line
		this.g.strokeWeight(scale5);
		this.g.line(xlegend+15, ylegend+130, xlegend+50,ylegend+130);

		//fifth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText5, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+135));
		
		//sixth line
		this.g.strokeWeight(scale6);
		this.g.line(xlegend+15, ylegend+160, xlegend+50,ylegend+160);

		//sixth legend text
		this.g.fill(0,0,0);
		this.g.text(scaleText6, Math.round(xlegend+60 + 15 * 0.75f + 5 / 2),
				Math.round(ylegend+165));
		//end of legend building
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
//			System.out.println("node "+n.iterator().toString()+" lat: " +an.getLat()+" lon: "+an.getLon());
			
			//visualizing the centroids
			if(an instanceof Centroid)
			{
				//drawing centroid node on the map					
				NodeMarker city = new NodeMarker(new Location(an.getLat(),an.getLon()), 1);
				int r=150, g=130, b=180;
				city.setNodeColor(r,g,b);
				city.setStroke(0,0,128);
				city.setFill(true);
				NodeMarkers.add(city);
//				System.out.println("centroid is drawn:"+an.getId()+", "+an.getName());

				//drawing label for the centroid
				font = loadFont("Helvetica-16.vlw");	
				//				LabeledMarker nodeIDMarker = new LabeledMarker 
				//						(new Location(an.getLat(),an.getLon()),
				//						an.getName()+" id: "+an.getId()+"_"+((Centroid)an).getISOAlpha3(),font, 5);		

				LabeledMarker nodeIDMarker = new LabeledMarker (new Location(an.getLat(),an.getLon()), an.getName()+"_"
						+((Centroid)an).getRegion(),font, 2);
				LabeledMarkers.add(nodeIDMarker);
			}
			
			//visualizing port node
			if(an instanceof PortNode)
			{
				//adding port node
				PortMarker port = new PortMarker(new Location(an.getLat(),an.getLon()), 1);
				port.setRectSize(1, 1);
				port.setNodeColor(0, 0, 128);
				port.setStrokeWeight(1);
				NodeMarkers.add(port);
				
				//adding throughput and transhipment information
				int throughput = (int) (((PortNode) an).getThroughput()/2.25E5);
				NodeMarker p = new NodeMarker(new Location(an.getLat(),an.getLon()), throughput);
				int r=70, g=130, b=180;
				p.setNodeColor(r,g,b);
				p.setStroke(0,0,128);
				p.setFill(true);
//				NodeMarkers.add(p);
				
				//drawing label for the ports
				font = loadFont("Helvetica-16.vlw");	
				LabeledMarker portIDMarker = new LabeledMarker (new Location(an.getLat(),an.getLon()),
						an.getName()+" id: "+an.getId()+" throughput: "+Math.round(((PortNode)an).getThroughput()),font, 2);		
				LabeledMarkers.add(portIDMarker);
			}
		}//end of loop	

		//adding markers for ports
		map.addMarkers(LabeledMarkers);	
		//adding markers for nodes
//		map.addMarkers(NodeMarkers);			

		//loop through the edges of the maritimeGraph
		//drawing the original maritime network
		Collection<AbstractEdge> edges=this.maritimeGraph.getEdges();
		double maxFlow=0.0;
		for (AbstractEdge abstractEdge : edges) {
			double flow=abstractEdge.getAssignedflow();

			Pair <AbstractNode> endPoints = abstractEdge.getEndpoints();
			Double lineWidth = (flow/scale);
			
			//linewidth for visualizing CO2 emission
//			Double lineWidth = (abstractEdge.getCO2()/scale);
					
			//loop to create labels for each link
			AbstractNode firstNode =endPoints.getFirst();
			AbstractNode secondNode =endPoints.getSecond();
			
			if(maxFlow<abstractEdge.getAssignedflow())
				maxFlow=abstractEdge.getAssignedflow();
			
//			System.out.println("printing flows from,to: "+firstNode.getId()+","+secondNode.getId()+
//					","+lineWidth);
			
//			adding link marker
//			addLinkMarker(LinkMarkers, lineWidth, firstNode, secondNode);			
			
			//visualizing edges that are also without flow
			if(lineWidth<=0)
			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
				lineWidth=1.0;
//				continue;
			} 
			else if (lineWidth>0 && lineWidth<=1)
			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
				lineWidth=1.0;
			}
			
			else if (lineWidth>0 && lineWidth<=1)
			{
//				System.out.println("negative or zero flow is detected :"+lineWidth);
				lineWidth=1.0;
			}
			
			float mapLineWidth= lineWidth.floatValue();			
//			System.out.println("map width:"+mapLineWidth);

			//CO2 emissions
//			int r=119, g=136, b=153;
			
			if (abstractEdge.getMode().equalsIgnoreCase("Road 2 lanes"))
			{
//				int r=113, g=113, b=141;
//				int r=72,g=209,b=204;		
				//blue
				int r=80, g=80, b=224;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
//				System.out.println("drawing road 2 lanes, from, to: "+endPoints.getFirst().getId()+","+endPoints.getSecond().getId()+","+mapLineWidth);
			}
			
			if (abstractEdge.getMode().equalsIgnoreCase("Railway"))
			{
				//black
				int r=0,g=0,b=0;
				//light gray for dark matter map background
//				int r=190, g=190, b=190;

				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
//				System.out.println("drawing railway, from, to: "+endPoints.getFirst().getId()+","+endPoints.getSecond().getId()+","+mapLineWidth);
			}
			
			//currently not used, only when more extensive maritime network is used
			if (abstractEdge.getMode().equalsIgnoreCase("Maritime Link"))
			{
				int r=113, g=113, b=141;
				//blue
//				int r=80, g=80, b=224;	
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
					
			else if (abstractEdge.getMode().equalsIgnoreCase("Connector"))
			{
//				//light gray only for dark matter map
//				int r=190, g=190, b=190;				
//				//red
				int r=255;
				int g=15; 
				int b=25;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);	
			}
			
			if(abstractEdge.getMode().equalsIgnoreCase("Handling"))
			{
				int r=34, g=14, b=210;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
			
			if(abstractEdge.getMode().equalsIgnoreCase("Station_Rail_Connector"))
			{
				int r=113, g=113, b=141;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
			
			if(abstractEdge.getMode().equalsIgnoreCase("Road_Station_Connector"))
			{
//				int r=80, g=80, b=224;
				
				//blue to make it similar to rail
				int r=80, g=80, b=224;
				drawServiceEdges(ServiceEdgeMarkers, endPoints, mapLineWidth,r,g,b);
			}
		}		
		this.scaleGap=maxFlow/6;
		map.addMarkers(ServiceEdgeMarkers);	
		
		//if we want to display volume label in each link
//		map.addMarkers(LinkMarkers);
		// end of loop
	}

	private void addLinkMarker(List<Marker> LinkMarkers, Double lineWidth, AbstractNode firstNode,
			AbstractNode secondNode) {
		LabeledMarker linkMarker =new LabeledMarker 
				(new Location(
						firstNode.getLat()+((secondNode.getLat()-firstNode.getLat())/2),
						firstNode.getLon()+((secondNode.getLon()-firstNode.getLon())/2)),
								String.valueOf(Math.round(lineWidth/1000000))
										,font, 1);	
		LinkMarkers.add(linkMarker);
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
	
	public static void main(String[] args) {
		PApplet.main(new String[] { GlobalMaritimeShipping.class.getName() });
	}
}
	
	
	
