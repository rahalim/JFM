package jfm.network;

public class MaritimeNode extends AbstractNode {
	static {
		NetworkComponentFactory.getFactory().registerProduct("MaritimeNode",MaritimeNode.class);
	}
	
	public MaritimeNode(double lat, double lon, String name, int id) {
		super(lat, lon, name, id);
	}
	
}
