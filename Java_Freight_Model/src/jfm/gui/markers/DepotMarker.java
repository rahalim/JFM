package jfm.gui.markers;

import java.awt.Color;

import processing.core.PGraphics;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.SimplePointMarker;

public class DepotMarker extends SimplePointMarker {
	private int red, green,blue;
	private float length;
	private float width;

	public DepotMarker(Location location, int rad) {
			super(location);
			//diameter = rad;
			radius=rad;
			}

	public void drawIn(PGraphics pg, float x, float y) {
		}
	
	public void setTriSize(float l, float w)
	{
		this.length=l;
		this.width=w;
	}
	
	public void setNodeColor(int r, int g, int b)
	{
		this.red=r;
		this.blue=b;
		this.green=g;
	}

	public void draw(PGraphics pg, float x, float y) {
		pg.stroke(this.red, this.green,this.blue);
		pg.fill(this.red, this.green,this.blue);
		pg.triangle(x-this.length, y-this.width, x+this.length, y-this.width, x, y+this.width);
	}
}