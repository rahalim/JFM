package jfm.gui;

import processing.core.PGraphics;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.SimplePointMarker;

public class PortMarker extends SimplePointMarker {
	private int red, green,blue;
	private float length;
	private float width;

	public PortMarker(Location location, int rad) {
		super(location);
		radius = rad;
	}

	public void drawIn(PGraphics pg, float x, float y) {
	}

	public void setRectSize(float l, float w)
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
		//pg.fill(100);
		pg.noFill();
		pg.stroke(this.red, this.green,this.blue);
		pg.rect(x, y, this.length, this.width);
	}
}


