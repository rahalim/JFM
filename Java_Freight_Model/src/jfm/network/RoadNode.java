package jfm.network;


public class RoadNode extends AbstractNode {
	
	private double DCCapacity;
	
	static{	
		NetworkComponentFactory.getFactory().registerProduct("RoadNode", RoadNode.class);
	}

	public RoadNode(int id, double lat, double lon, String name) {
		super(lat, lon, name, id);
	}
	
}
