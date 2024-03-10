package jfm.models;

import java.io.Serializable;

public class ModeShare implements Serializable {
	

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private double [] time=new double [25];
    //asc:alternative specific coefficient
    private double [] asc=new double [6];
    private double [] dist= new double [6];

	/**
     * Default constructor
     */
    public ModeShare() {
    }

	/**
	 * @return the time
	 */
	public double [] getTime() {
		return time;
	}

	/**
	 * @param time the time to set
	 */
	public void setTime(double [] time) {
		this.time = time;
	}

	/**
	 * @return the asc
	 */
	public double [] getAsc() {
		return asc;
	}

	/**
	 * @param asc the asc to set
	 */
	public void setAsc(double [] asc) {
		this.asc = asc;
	}

	/**
	 * @return the dist
	 */
	public double [] getDist() {
		return dist;
	}

	/**
	 * @param dist the dist to set
	 */
	public void setDist(double [] dist) {
		this.dist = dist;
	}

}
