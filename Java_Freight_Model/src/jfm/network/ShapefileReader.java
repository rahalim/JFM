package jfm.network;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.emf.ecore.util.EContentsEList.FeatureIterator;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.graph.build.feature.FeatureGraphGenerator;
import org.geotools.graph.build.line.BasicLineGraphGenerator;
import org.geotools.graph.build.line.LineStringGraphGenerator;
import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.basic.BasicGraph;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Coordinates;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;

/**
 * Prompts the user for a shapefile and displays the contents on the screen in a map frame.
 *
 * <p>This is the GeoTools Quickstart application used in documentationa and tutorials. *
 */
public class ShapefileReader 
{
	String shapefiledir;
	File file;
	SimpleFeatureSource featureSource;
	SimpleFeatureCollection features;

    public ShapefileReader (boolean readFromFile) throws IOException
    {
    	if(readFromFile)
    	{
    		// display a data store file chooser dialog for shapefiles
    		this.file = JFileDataStoreChooser.showOpenFile("shp", null);
    		if (file == null) {
    			return;
    		}
    		readShapefile();
    		//visualize the shapefile
    		visualizeShapeFile(featureSource);
    	}
    }
    
    public ShapefileReader (String dir) throws IOException
    {
    	this.shapefiledir=dir;
    	file  = new File (this.shapefiledir);
    	this.readShapefile();
    }
    

	private void readShapefile() throws IOException
    {
    	FileDataStore store = FileDataStoreFinder.getDataStore(file);
    	
    	this.featureSource = store.getFeatureSource();
    	FeatureType schema = featureSource.getSchema();
    	this.features = featureSource.getFeatures();
    }

	public BasicGraph generateGraph() {
		//create a linear graph generate
    	LineStringGraphGenerator lineStringGen = new LineStringGraphGenerator();

    	//wrap it in a feature graph generator
    	FeatureGraphGenerator featureGen = new FeatureGraphGenerator( lineStringGen );

    	//throw all the features into the graph generator
    	SimpleFeatureIterator iter =  features.features();
    	try {
    		//        	int f=0;
    		while(iter.hasNext())
    		{
    			SimpleFeature feature = (SimpleFeature) iter.next();
//    			int id=Integer.valueOf((String)feature.getAttribute("ID"));
//    			System.out.println("feature ID:"+id);
    			featureGen.add( feature );     	     
    		}
    	} 
    	finally 
    	{
    		iter.close();
    	}
    	//create a graph out of a shapefile
    	BasicGraph graph = (BasicGraph) featureGen.getGraph();        
    	System.out.println("graph is created");
    	return graph;
	}


	private void visualizeShapeFile(SimpleFeatureSource featureSource) {
		// Create a map content and add our shapefile to it
    	MapContent map = new MapContent();
    	map.setTitle("Quick View");

    	Style style = SLD.createSimpleStyle(featureSource.getSchema());
    	Layer layer = new FeatureLayer(featureSource, style);
    	map.addLayer(layer);

    	// Now display the map
    	JMapFrame.showMap(map);
	}
}
