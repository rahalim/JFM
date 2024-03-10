package jfm.choiceset;

import java.util.ArrayList;
import java.util.HashMap;
import jfm.network.AbstractEdge;

public class ChoiceSet {
	private ArrayList<Route> routes;

	public ChoiceSet(ArrayList<Route> routes){
		this.routes = routes;
		
		HashMap<AbstractEdge, Integer> counter = new HashMap<AbstractEdge, Integer>();
		for (Route route : routes) {
			for(AbstractEdge edge: route.getPath()){
				if(counter.containsKey(edge)){
					Integer newValue = counter.get(edge)+1; 
					counter.put(edge, newValue);
				}else{
					counter.put(edge, new Integer(1));
				}
			}
		}

		
		/*
		 * each edge in the counter with a value higher than 1 has an overlap 
		 * if edge value == 1, there is no overlap. This fact can be used to
		 * determine the path size overlap variable for each route
		 * 
		 * Sr = sum over links in route (length of shared link / length of route) 
		 *      * (1/(number of times link is shared in choice set))
		 * 
		 */
		for (Route route : routes) {
			double sr = 0;
			int count;
			for(AbstractEdge edge: route.getPath()){
				count = counter.get(edge);
				sr += (edge.getLength() / route.getLength()) * 
						(1/((double)count));
		}
			
			route.setSr(sr);
		}	
	}
	
	//reset should also reset all the routes in the choiceMap to avoid
	//objects having strong references to keep on being alive 
	public void reset(){
		for (Route route : this.routes) {
			route.resetAssignedFlow();	
			route.getPath().clear();
		}
		this.routes.clear();
	}
	
	public void resetRouteFlow(){
		for (Route route : this.routes) {
			route.resetAssignedFlow();		
		}
	}
	
	/**
	 * 
	 * @return routes
	 */
	public ArrayList<Route> getRoutes(){
		return this.routes;
	}
	
}
