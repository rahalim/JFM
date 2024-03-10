package jfm.models;

import java.io.Serializable;

public class ModeShareCost extends ModeShare {
	

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
    //asc:alternative specific constant
	private double [] asc = new double [3];
    private double [] time_coeff = new double[3];
    private double [] cost_coeff = new double[3];
    private double [] reliability = new double[3];
    private double [] risk_safety = new double[3];

	/**
     * Default constructor
     */
    public ModeShareCost() {
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

	public double[] getTime_Coeff() {
		return time_coeff;
	}

	public void setTime_Coeff(double[] time) {
		this.time_coeff = time;
	}

	public double[] getCost_Coeff() {
		return cost_coeff;
	}

	public void setCost_Coeff(double[] cost) {
		this.cost_coeff = cost;
	}

	public double [] getReliability_Coeff() {
		return reliability;
	}

	public void setReliability_Coeff(double [] reliability) {
		this.reliability = reliability;
	}

	public double [] getRisk_safety_Coeff() {
		return risk_safety;
	}

	public void setRisk_safety_Coeff(double [] risk_safety) {
		this.risk_safety = risk_safety;
	}
}
