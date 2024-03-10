package jfm.gui.markers;

import processing.core.PGraphics;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.SimplePointMarker;

public class NodeMarker extends SimplePointMarker {
	//fill color
	private int red, green,blue;
	// default stroke color
	private int r=0,g=153,b=153;
	private boolean fill=false;

	public NodeMarker(Location location, double rad) {
			super(location);
			//diameter = (float) rad;
			radius =(float) rad;
			}

	public void drawIn(PGraphics pg, float x, float y) {
		}
	
	public void setNodeColor(int r, int g, int b)
	{
		this.red=r;
		this.blue=b;
		this.green=g;
	}
	
	public void setStroke(int r, int g, int b)
	{
		this.r=r;
		this.b=b;
		this.g=g;
	}
	
	public void setFill(boolean val)
	{
		this.fill=val;
	}

	public void draw(PGraphics pg, float x, float y) {
		//pg.fill(100);
		if(fill)
		{
			pg.fill(red,green,blue);
		}
		else{
			pg.noFill();
		}
		pg.stroke(r,g,b);
		//pg.ellipse(x, y, diameter, diameter);
		pg.ellipse(x, y, radius,radius);
	}
}
