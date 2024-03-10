package jfm.commodities;

import java.io.Serializable;

/**
 * Commodity
 */	
public class Commodity implements Serializable {

    private int ID=0;
    private int transport=0;
    private int model=0;
    private String name;
    private int [] active=new int [6];
    private double truckload=0;
    private double TEU_load=0;
    private int container=0;
	/**
     * Default constructor
     */
    public Commodity() {
    }

	@Override
	public String toString() {
		return name;
	}

	/**
	 * @return the iD
	 */
	public int getID() {
		return ID;
	}

	/**
	 * @param iD the iD to set
	 */
	public void setID(int iD) {
		ID = iD;
	}

	/**
	 * @return the transport
	 */
	public int getTransport() {
		return transport;
	}

	/**
	 * @param transport the transport to set
	 */
	public void setTransport(int transport) {
		this.transport = transport;
	}

	/**
	 * @return the model
	 */
	public int getModel() {
		return model;
	}

	/**
	 * @param model the model to set
	 */
	public void setModel(int model) {
		this.model = model;
	}

	/**
	 * @return the active
	 */
	public int [] getActive() {
		return active;
	}

	/**
	 * @param active the active to set
	 */
	public void setActive(int [] active) {
		this.active = active;
	}

	/**
	 * @return the truckload
	 */
	public double getTruckload() {
		return truckload;
	}

	/**
	 * @param truckload the truckload to set
	 */
	public void setTruckload(double truckload) {
		this.truckload = truckload;
	}

	/**
	 * @return the tEU_load
	 */
	public double getTEU_load() {
		return TEU_load;
	}

	/**
	 * @param tEU_load the tEU_load to set
	 */
	public void setTEU_load(double tEU_load) {
		TEU_load = tEU_load;
	}

	/**
	 * @return the container
	 */
	public int getContainer() {
		return container;
	}

	/**
	 * @param container the container to set
	 */
	public void setContainer(int container) {
		this.container = container;
	}
	
	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * This number is here for model snapshot storing purpose<br>
	 * It needs to be changed when this class gets changed
	 */ 
	private static final long serialVersionUID = 1L;

}