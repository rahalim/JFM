package jfm.models;

import java.io.Serializable;

public class ValueToWeight implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private double [] intercept=new double [6];
	private double [] percento=new double [6];
	private double [] percentd=new double [6];
	private double [] lang=new double [6];
	private double [] rta=new double [6];
	private double [] lngdpcod=new double [6];
	private double [] percentgdpco=new double [6];
	private double [] percentgdpcd=new double [6]; 
	private double [] contig=new double [6];
	//commodities, mode
	private double [][] time=new double [25][6];
	private double [] avdist=new double [6];


	/**
	 * Default constructor
	 */
	public ValueToWeight() {
	}


	public double [] getIntercept() {
		return intercept;
	}


	public void setIntercept(double [] intercept) {
		this.intercept = intercept;
	}


	public double [] getPercento() {
		return percento;
	}


	public void setPercento(double [] percento) {
		this.percento = percento;
	}


	/**
	 * @return the percentd
	 */
	public double [] getPercentd() {
		return percentd;
	}


	/**
	 * @param percentd the percentd to set
	 */
	public void setPercentd(double [] percentd) {
		this.percentd = percentd;
	}


	/**
	 * @return the lang
	 */
	public double [] getLang() {
		return lang;
	}


	/**
	 * @param lang the lang to set
	 */
	public void setLang(double [] lang) {
		this.lang = lang;
	}


	/**
	 * @return the lngdpcod
	 */
	public double [] getLngdpcod() {
		return lngdpcod;
	}


	/**
	 * @param lngdpcod the lngdpcod to set
	 */
	public void setLngdpcod(double [] lngdpcod) {
		this.lngdpcod = lngdpcod;
	}


	/**
	 * @return the rta
	 */
	public double [] getRta() {
		return rta;
	}


	/**
	 * @param rta the rta to set
	 */
	public void setRta(double [] rta) {
		this.rta = rta;
	}


	/**
	 * @return the percentgdpco
	 */
	public double [] getPercentgdpco() {
		return percentgdpco;
	}


	/**
	 * @param percentgdpco the percentgdpco to set
	 */
	public void setPercentgdpco(double [] percentgdpco) {
		this.percentgdpco = percentgdpco;
	}


	/**
	 * @return the percentgdpcd
	 */
	public double [] getPercentgdpcd() {
		return percentgdpcd;
	}


	/**
	 * @param percentgdpcd the percentgdpcd to set
	 */
	public void setPercentgdpcd(double [] percentgdpcd) {
		this.percentgdpcd = percentgdpcd;
	}


	/**
	 * @return the avdist
	 */
	public double [] getAvdist() {
		return avdist;
	}


	/**
	 * @param avdist the avdist to set
	 */
	public void setAvdist(double [] avdist) {
		this.avdist = avdist;
	}


	/**
	 * @return the contig
	 */
	public double [] getContig() {
		return contig;
	}


	/**
	 * @param contig the contig to set
	 */
	public void setContig(double [] contig) {
		this.contig = contig;
	}


	/**
	 * @return the time
	 */
	public double [][] getTime() {
		return time;
	}


	/**
	 * @param time the time to set
	 */
	public void setTime(double [][] time) {
		this.time = time;
	}

}
