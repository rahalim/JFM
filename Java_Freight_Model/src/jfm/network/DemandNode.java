package jfm.network;

public class DemandNode extends AbstractNode {

	private double demand;
	
	static{	
		NetworkComponentFactory.getFactory().registerProduct("DemandNode",DemandNode.class);
	}
	
	public DemandNode(int id, double lat, double lon, String name, double demand) {
		super(lat, lon, name, id);
		this.demand= demand;
	}
	
	public double getDemand()
	{
		return this.demand;
	}

}
