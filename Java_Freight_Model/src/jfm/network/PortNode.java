package jfm.network;


public class PortNode extends AbstractNode {
	private String type;
	private double throughput = 0;
	private double transhipment = 0;
	private double handlingCosts;
	private double handlingTime;
	private double observedTranshipment;
	private double observedThroughput;
	private String seaArea;
	private double sumDerivativeFlow=0;
	private double totalFlowLastIteration=0;


	static{
		NetworkComponentFactory.getFactory().registerProduct("PortNode", PortNode.class);
	}
	
	/**
	 * 
	 * @param lat latitude
	 * @param lon longitude
	 * @param name name of the port
	 * @param id id of the port
	 * @param label shorthand label of the port
	 * @param country country in which the port is located
	 * @param capacity capacity of the port
	 * @param handlingCosts
	 * @param transhipment
	 * @param throughput
	 * @param seaArea 
	 * 
	 */
	public PortNode(double lat, double lon, String name, int id, String type,
			double handlingTime, double handlingCosts, double throughput, double transhipment) 
	{
		
		super(lat, lon, name, id);
		
		this.type = type;
		this.handlingCosts=handlingCosts;
		//both the observed throughput and transhipment are zeros at the moment
		this.observedThroughput= throughput;
		this.observedTranshipment= transhipment;
		this.handlingTime=handlingTime;		
	}
	
	public void resetAssignedFlow(){
		this.throughput = 0;
		this.transhipment = 0;
		this.sumDerivativeFlow=0;
	}
		
	
	/**
	 * 
	 * @return throughput
	 */
	public double getThroughput() {
		return throughput;
	}

	/**
	 * 
	 * @return transhipment
	 */
	public double getTranshipment() {
		return transhipment;
	}


	/**
	 * 
	 * @param throughput
	 */
	public void addThroughput(double throughput) {
		this.throughput += throughput;
	}

	/**
	 * 
	 * @param transhipment
	 */
	public void addTranshipment(double transhipment) {
		this.transhipment += transhipment;
	}


	public double getObservedTranshipment() {
		return observedTranshipment;
	}

	public double getObservedThroughput() {
		return observedThroughput;
	}

	/**
	 * @return the handlingCosts
	 */
	public double getHandlingCosts() {
		return handlingCosts;
	}

	/**
	 * @param handlingCosts the handlingCosts to set
	 */
	public void setHandlingCosts(double handlingCosts) {
		this.handlingCosts = handlingCosts;
	}

	public void setObservedThroughput(double observedThroughput) {
		this.observedThroughput = observedThroughput;
	}
	
	public String getSeaArea() {
		return this.seaArea;
	}

	public double getSumDerivativeFlow() {
		return sumDerivativeFlow;
	}

	public void setSumDerivativeFlow(double sumDerivativeFlow) {
		this.sumDerivativeFlow = sumDerivativeFlow;
	}

	public double getTotalFlowLastIteration() {
		return totalFlowLastIteration;
	}

	public void setTotalFlowLastIteration(double totalFlowLastIteration) {
		this.totalFlowLastIteration = totalFlowLastIteration;
	}

}
