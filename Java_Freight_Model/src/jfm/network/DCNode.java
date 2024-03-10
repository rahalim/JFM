package jfm.network;



public class DCNode extends AbstractNode {
	
	private double DCcapacity;
	private double observedDCValue;

	static{	
		NetworkComponentFactory.getFactory().registerProduct("DCNode",DCNode.class);
	}
	public DCNode(int id, double lat, double lon, String name, double capacity, double warehouseActivity) {
		super(lat, lon, name, id);
		this.DCcapacity=capacity;
		this.observedDCValue=warehouseActivity;
		
	}
	
	public double getDCCapacity()
	{
		return this.DCcapacity;
	}
	
	public double getDCSize()
	{
		return this.observedDCValue;
	}

}
