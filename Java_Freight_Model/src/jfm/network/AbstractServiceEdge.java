package jfm.network;

import java.util.List;

import edu.uci.ics.jung.graph.util.Pair;


public abstract class AbstractServiceEdge extends AbstractEdge {
	protected double costs;
	private List<AbstractEdge> shortestPath;
	
	
	public AbstractServiceEdge(Pair<AbstractNode> endpoints, double costs,
			List<AbstractEdge> shortestPath) {
		super(endpoints);
		this.costs = costs;
		this.shortestPath = shortestPath;
	}

	/**
	 * 
	 * @return costs
	 */
	public double getCosts(){
		return this.costs;
	}
	
	/**
	 * add assigned flow to the edges in the physical network that
	 * are on the shortest path.
	 */
	public void addAssignedFlow(double assignedFlow){
		this.assignedFlow += assignedFlow;
		
		for (AbstractEdge edge : this.shortestPath) {
			edge.addAssignedFlow(assignedFlow);
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public List<AbstractEdge> getShortestPath()
	{
		return this.shortestPath;
	}

}
