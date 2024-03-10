package jfm.gui.markers;

import processing.core.PGraphics;

import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.SimplePointMarker;

public class ClientMarker extends SimplePointMarker {

	public ClientMarker(Location location) {
			super(location);
			//diameter = 5;
			radius=5;
			}

	public void drawIn(PGraphics pg, float x, float y) {
		}

	public void draw(PGraphics pg, float x, float y) {
			pg.fill(200);
			pg.stroke(200);
		//	pg.ellipse(x, y, diameter, diameter);
			pg.ellipse(x, y, radius, radius);
		}
	}
