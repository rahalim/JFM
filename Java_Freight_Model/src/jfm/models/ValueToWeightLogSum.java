package jfm.models;

public class ValueToWeightLogSum extends ValueToWeight{
	
	private static final long serialVersionUID = 1L;
	private double  intercept_L;
	private double  percento_L;
	private double  percentd_L;
	private double  lang_L;
	private double  rta_L;
	private double  lngdpcod_L;
	private double  percentgdpco_L;
	private double  percentgdpcd_L; 
	private double  contig_L;
	private double[] LogSum=new double [25];
	
	
	/**
	 * Default constructor
	 */
	public ValueToWeightLogSum() {
	}

	public double getIntercept_L() {
		return intercept_L;
	}

	public void setIntercept_L(double intercept) {
		this.intercept_L = intercept;
	}


	public double getPercento_L() {
		return percento_L;
	}


	public void setPercento_L(double percento) {
		this.percento_L = percento;
	}


	/**
	 * @return the percentd
	 */
	public double getPercentd_L() {
		return percentd_L;
	}

	/**
	 * @param percentd the percentd to set
	 */
	public void setPercentd_L(double percentd) {
		this.percentd_L = percentd;
	}


	/**
	 * @return the lang
	 */
	public double getLang_L() {
		return lang_L;
	}


	/**
	 * @param lang the lang to set
	 */
	public void setLang_L(double  lang) {
		this.lang_L = lang;
	}


	/**
	 * @return the lngdpcod
	 */
	public double getLngdpcod_L() {
		return lngdpcod_L;
	}


	/**
	 * @param lngdpcod the lngdpcod to set
	 */
	public void setLngdpcod_L(double lngdpcod) {
		this.lngdpcod_L = lngdpcod;
	}


	/**
	 * @return the rta
	 */
	public double getRta_L() {
		return rta_L;
	}


	/**
	 * @param rta the rta to set
	 */
	public void setRta_L(double rta) {
		this.rta_L = rta;
	}


	/**
	 * @return the percentgdpco
	 */
	public double getPercentgdpco_L() {
		return percentgdpco_L;
	}


	/**
	 * @param percentgdpco the percentgdpco to set
	 */
	public void setPercentgdpco_L(double percentgdpco) {
		this.percentgdpco_L = percentgdpco;
	}


	/**
	 * @return the percentgdpcd
	 */
	public double getPercentgdpcd_L() {
		return percentgdpcd_L;
	}


	/**
	 * @param percentgdpcd the percentgdpcd to set
	 */
	public void setPercentgdpcd_L(double percentgdpcd) {
		this.percentgdpcd_L = percentgdpcd;
	}

	/**
	 * @return the contig
	 */
	public double getContig_L() {
		return contig_L;
	}

	/**
	 * @param contig the contig to set
	 */
	public void setContig_L(double contig) {
		this.contig_L = contig;
	}
	
	/**
	 * @return the time
	 */
	public double [] getLogSum() {
		return LogSum;
	}
	
	public void setLogSum(int id, double logSumCoef) {
		this.LogSum[id]=logSumCoef;
	}
}
