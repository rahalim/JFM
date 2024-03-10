package jfm.gui.markers;

import java.util.List;

import processing.core.PGraphics;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.AbstractShapeMarker;
import de.fhpotsdam.unfolding.marker.Marker;
import de.fhpotsdam.unfolding.utils.MapPosition;

/**
 * A special marker to connect two markers.  This is very similar to a SimpleLinesMarker, 
 * only that it uses the locations of two given markers.
 */
public class EdgeMarker extends AbstractShapeMarker {

	public EdgeMarker(Marker fromMarker, Marker toMarker) {
		addLocations(fromMarker.getLocation());
		addLocations(toMarker.getLocation());
	}
	
	public EdgeMarker (Location a, Location b){
		addLocations(a);
		addLocations(b);
	}

	@Override
	public void draw(PGraphics pg, List<MapPosition> mapPositions) {
		MapPosition from = mapPositions.get(0);
		MapPosition to = mapPositions.get(1);
		
		pg.stroke(72,209,204);
		pg.strokeWeight(2);
		pg.line(from.x, from.y, to.x, to.y);
		
	}

}
