package jfm.gui.markers;

import java.util.List;

import processing.core.PGraphics;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.AbstractShapeMarker;
import de.fhpotsdam.unfolding.utils.MapPosition;

public class ServiceEdgeMarker extends AbstractShapeMarker {

	//color composition of the edge
	private int red,green,blue;
	private int r,g,b;
	
	
	private float strokeWidth;
	
	public ServiceEdgeMarker (Location a, Location b){
		addLocations(a);
		addLocations(b);
	}
	
	public void setEdgeColor(int r, int g, int b)
	{
		this.red=r;
		this.green=g;
		this.blue=b;
	}
	
	public void setStrokeWidth(float width)
	{
		this.strokeWidth=width;
	}
	
	public void setStrokeColor(int r, int g, int b)
	{
		this.r=r;
		this.g=g;
		this.b=b;
	}
	
	@Override
	public void draw(PGraphics pg, List<MapPosition> mapPositions) {

		MapPosition from = mapPositions.get(0);
		MapPosition to = mapPositions.get(1);
		//fill color
//		pg.fill(red,green,blue);
		pg.strokeWeight(this.strokeWidth);
		pg.stroke(this.red,this.green,this.blue);
		pg.line(from.x, from.y, to.x, to.y);
		
	}
}
