package jfm.network;

public class AirportNode extends AbstractNode {
	private int flights;
	private String label;
	private String country;

	public AirportNode(double lat, double lon, String name, int id) {
		super(lat, lon, name, id);
	}
	
	public AirportNode (double lat, double lon, String name, int id, String label,
			String country, double[] capacity, double[] penalty, double[] penalty_ref, double[] handlingTime)
	{
		super (lat,lon,name, id);
		
		this.label=label;
		this.country=country;
		this.setCapacity(capacity);
		this.setCost(penalty);
		this.setCost_ref(penalty_ref);
		this.setHandlingTime(handlingTime);
	}

	/**
	 * @return the flights
	 */
	public int getFlights() {
		return flights;
	}

	/**
	 * @param flights the flights to set
	 */
	public void setFlights(int flights) {
		this.flights = flights;
	}

	/**
	 * @return the country
	 */
	public String getCountry() {
		return country;
	}

	/**
	 * @param country the country to set
	 */
	public void setCountry(String country) {
		this.country = country;
	}
}
