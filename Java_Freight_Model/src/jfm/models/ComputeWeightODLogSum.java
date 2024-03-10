package jfm.models;

import java.util.ArrayList;
import java.util.HashMap;

import jfm.commodities.Commodity;
import jfm.network.Centroid;
import jfm.network.MultiModalNetwork;
import jfm.network.MultiModalNetworkCostNoSQL;
import jfm.scenario.ODdata;

public class ComputeWeightODLogSum {

	private ODdata baseOD;
	private HashMap<Integer, Centroid> centroids;
	private ArrayList<Commodity> commodities;
	private ModeShareCost modeShare;
	private int modes;
	private long endTime, processTime, startTime;
	private int commodity =0; //index for commodity, for now it's zero since we don't have any differentiations
	private int active =1; //all modes are active

	public ComputeWeightODLogSum(MultiModalNetworkCostNoSQL freightNetwork, int yearIndex, long startTime)
	{
		System.out.println("computing weight od for year: "+yearIndex);
		this.centroids =freightNetwork.getCentroids();
		this.commodities =freightNetwork.getCommodities();

		//this OD file has to be resetted per iteration
		//not necessarily because the previous values are going to be overriden by the new values
		//but we can clear the od values by modes and od weights by modes
		this.baseOD = freightNetwork.getBaseOD();
		this.baseOD.clearMatrices();

		//cost-based mode share
		this.modeShare =freightNetwork.getModeShareCost();		
		this.modes =freightNetwork.getModes();
		this.startTime=startTime;

		//this.compute(yearIndex, freightNetwork);
	}

	public void compute (int yearIndex, MultiModalNetworkCostNoSQL freightNetwork)
	{	
		//1.compute/update the logsum for origin and destination pairs based on the available modes	
		for(int origin=1;origin<=centroids.size();origin++)
		{
			//for each destination
			for(int destination=1;destination<=centroids.size();destination++)
			{
				Centroid c_origin = centroids.get(origin);
				Centroid c_destination = centroids.get(destination);

				//3. computing the utility for different mode by commodities
				//total is denominator of multinomial logit model
				double total=0;
				for(int mode=0;mode<modes;mode++)
				{	
					double travelTime = baseOD.getODTime(origin, destination, mode);
					double travelDistance = baseOD.getODDistance(origin, destination, mode);

					//using the updated cost from the previous iteration
					double cost = baseOD.getODCost(origin, destination, mode);
					double tradeWeight= baseOD.getWeightOD_year(origin, destination, commodity, yearIndex);
															
//					System.out.println("origin:"+c_origin.getId()+", destination:"+c_destination.getId()+" mode: "+mode
//							+" travel time: "+travelTime+" travel distance: "+travelDistance+" active: "+active+","+"trade weight "+tradeWeight);
					
//					System.out.println("alt spec const for mode 0: "+modeShare.getAsc()[mode]+","
//							+ "coefficient for cost, time, reliability, "+modeShare.getCost_Coeff()[mode]+","
//							+ modeShare.getTime_Coeff()[mode]+","+modeShare.getReliability_Coeff()[mode]+","
//							+ modeShare.getRisk_safety_Coeff()[mode]);

					//special case for mode rail
					if (mode==0)
					{	//if distance is less than 350 km is not considered as an alternative
						//if travel time >0 then it means it's possible to transport with this mode
						if (travelTime>0 && travelDistance>=200)
						{
							
							//if the commodity is active then there is no penalty
							double util =(active-1)*1000000
									//asc for mode 1
//									+(4.68
									+(modeShare.getAsc()[mode]
									//mode specific cost coefficient TODO: check if it's necessary to multiply with the weight of the OD
									+modeShare.getCost_Coeff()[mode]*baseOD.getODCost(origin, destination, mode)
									//mode specific time coefficient
									+modeShare.getTime_Coeff()[mode]*baseOD.getODTime(origin, destination, mode)
									+modeShare.getReliability_Coeff()[mode]*baseOD.getReliability(origin, destination, mode)
									+modeShare.getRisk_safety_Coeff()[mode]*baseOD.getRisk_safety(origin, destination, mode));

							baseOD.setUtilityModes(origin,destination,mode,commodity,util);
							total+=Math.exp(baseOD.getUtilityByModes(origin,destination,mode)[commodity]);
//															System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" utility value is "+util);
						}	
					}

					//case for mode road, the same model is used for mode 3 (MDV), and 4 (LDV)
					else if (mode==1)
					{						
						//assigning the utility for commodity k with mode 1, distance less than 10000
						if(travelTime>0 && travelDistance>=150)
						{			
//							System.out.println("alt spec const for mode 1: "+modeShare.getAsc()[mode]+","
//									+ "coefficient for cost, time, reliability, "+modeShare.getCost_Coeff()[mode]+","
//									+ modeShare.getTime_Coeff()[mode]+","+modeShare.getReliability_Coeff()[mode]+","
//									+ modeShare.getRisk_safety_Coeff()[mode]);
							
							//assigning the utility for commodity k with mode 0
							double util = (active-1)*1000000
									//asc for mode 2
									+(modeShare.getAsc()[mode]
									//mode specific cost coefficient TODO: check if it's necessary to multiply with the weight of the OD
									+modeShare.getCost_Coeff()[mode]*baseOD.getODCost(origin, destination, mode)
									//mode specific time coefficient
									+modeShare.getTime_Coeff()[mode]*baseOD.getODTime(origin, destination, mode)
									+modeShare.getReliability_Coeff()[1]*baseOD.getReliability(origin, destination, mode)
									+modeShare.getRisk_safety_Coeff()[1]*baseOD.getRisk_safety(origin, destination, mode));

							baseOD.setUtilityModes(origin,destination,mode,commodity, util);
							total+=Math.exp(baseOD.getUtilityByModes(origin,destination,mode)[commodity]);
//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" utility value is "+util);
						}							
					}
					 //mode 3 (MDV), 
					else if (mode==3)
					{						
						if(travelTime>0 && travelDistance>80 && travelDistance<=150)
						{					
							//assigning the utility for commodity k with mode 0
							double util = (active-1)*1000000
									//asc for mode 2
									+(modeShare.getAsc()[1]
									//mode specific cost coefficient TODO: check if it's necessary to multiply with the weight of the OD
									+modeShare.getCost_Coeff()[1]*baseOD.getODCost(origin, destination, 1)
									//mode specific time coefficient
									+modeShare.getTime_Coeff()[1]*baseOD.getODTime(origin, destination, 1)
									+modeShare.getReliability_Coeff()[1]*baseOD.getReliability(origin, destination, 1)
									+modeShare.getRisk_safety_Coeff()[1]*baseOD.getRisk_safety(origin, destination, 1));

							baseOD.setUtilityModes(origin,destination,mode,commodity, util);
							total+=Math.exp(baseOD.getUtilityByModes(origin,destination,mode)[commodity]);
//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" utility value is "+util);
						}							
					}
					//mode 4 (LDV)
					else if (mode==4)
					{						
						if(travelTime>0 && travelDistance <=80)
						{			
							//assigning the utility for commodity k with mode 0
							double util = (active-1)*1000000
									//asc for mode 2
									+(modeShare.getAsc()[1]
									//mode specific cost coefficient TODO: check if it's necessary to multiply with the weight of the OD
									+modeShare.getCost_Coeff()[1]*baseOD.getODCost(origin, destination, 1)
									//mode specific time coefficient
									+modeShare.getTime_Coeff()[1]*baseOD.getODTime(origin, destination, 1)
									+modeShare.getReliability_Coeff()[1]*baseOD.getReliability(origin, destination, 1)
									+modeShare.getRisk_safety_Coeff()[1]*baseOD.getRisk_safety(origin, destination, 1));

							baseOD.setUtilityModes(origin,destination,mode,commodity, util);
							total+=Math.exp(baseOD.getUtilityByModes(origin,destination,mode)[commodity]);
//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" utility value is "+util);
						}							
					}
					//case for mode sea, for now it has not been accessed
					// this mode is artificial to complete main sea transport mode
					else if (mode==2)
					{
						if(travelTime>0)
						{
							double util = (active-1)*1000000
									//asc for mode 2
									+(modeShare.getAsc()[mode]
									//mode specific cost coefficient TODO: check if it's necessary to multiply with the weight of the OD
									+modeShare.getCost_Coeff()[mode]*baseOD.getODCost(origin, destination, mode)
									//mode specific time coefficient
									+modeShare.getTime_Coeff()[mode]*baseOD.getODTime(origin, destination, mode)
									+modeShare.getReliability_Coeff()[mode]*baseOD.getReliability(origin, destination, mode)
									+modeShare.getRisk_safety_Coeff()[mode]*baseOD.getRisk_safety(origin, destination, mode));

							baseOD.setUtilityModes(origin,destination,mode,commodity, util);
							//								System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" utility value is "+util);
							total+=Math.exp(baseOD.getUtilityByModes(origin,destination,mode)[commodity]);
						}							
					}
				}

				//4. mode share computation 
				for(int mode=0;mode<modes;mode++)	
				{
					double travelTime = baseOD.getODTime(origin, destination, mode);
					double travelDistance = baseOD.getODDistance(origin, destination, mode);
					//active denotes whether the mode of transport is available or not, if available, active=0, otherwise active=-1

//					System.out.println("origin:"+c_origin.getISOAlpha3()+", destination:"+c_destination.getISOAlpha3()+" mode: "+mode
//							+" travel time: "+travelTime+" travel distance: "+travelDistance+" active: "+active+" commodity model ID: "+comModelID);

					//if commodity k is active and there is time from centroid i to j for mode 1 and distance betweeen centroid i to j using mode 1 is below 15000
					//mode 0, rail
					if (mode==0)
					{
						if(active>0 && travelTime>0 && travelDistance>=350)
						{
							double nominator = Math.exp(baseOD.getUtilityByModes(origin, destination,mode)[commodity]);
							double modeShare = nominator/total;
							//								System.out.println("mode 1, nominator: "+nominator+" dominator: "+total);
							baseOD.setMode_share(origin, destination, mode, commodity, modeShare);
							//								System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+modeShare);
						}
						else
							//setting mode share of this mode into zero
							baseOD.setMode_share(origin, destination, mode, commodity, 0);
						//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+baseOD.getMode_share(origin, destination, mode)[commodity]);
					}

					//mode 1, road, HDV
					else if(mode==1)
					{
						if(active>0 && travelTime>0 && travelDistance>=150)
						{
							double nominator = Math.exp(baseOD.getUtilityByModes(origin, destination,mode)[commodity]);
							double modeShare = nominator/total;
							baseOD.setMode_share(origin, destination, mode, commodity, modeShare);
						}
						else
							baseOD.setMode_share(origin, destination, mode, commodity, 0);
						//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+baseOD.getMode_share(origin, destination, mode)[commodity]);
					}
					//MDV
					else if(mode==3)
					{
						if(active>0 && travelTime>0 && travelDistance>80 && travelDistance <=150)
						{
							double nominator = Math.exp(baseOD.getUtilityByModes(origin, destination,mode)[commodity]);
							double modeShare = nominator/total;
							baseOD.setMode_share(origin, destination, mode, commodity, modeShare);
						}
						else
							baseOD.setMode_share(origin, destination, mode, commodity, 0);
						//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+baseOD.getMode_share(origin, destination, mode)[commodity]);
					}
					//LDV
					else if(mode==4)
					{
						if(active>0 && travelTime>0 && travelDistance <=80)
						{
							double nominator = Math.exp(baseOD.getUtilityByModes(origin, destination,mode)[commodity]);
							double modeShare = nominator/total;
							baseOD.setMode_share(origin, destination, mode, commodity, modeShare);
						}
						else
							baseOD.setMode_share(origin, destination, mode, commodity, 0);
						//							System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+baseOD.getMode_share(origin, destination, mode)[commodity]);
					}
					//mode 2, sea
					else if(mode==2)
					{ 
						if(active>0 &&travelDistance>0)
						{
							double nominator = Math.exp(baseOD.getUtilityByModes(origin, destination,mode)[commodity]);
							double modeShare = nominator/total;
							baseOD.setMode_share(origin, destination, mode, commodity, modeShare);
							//								System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+modeShare);
						}
						else
							//set mode share to zero
							baseOD.setMode_share(origin, destination, mode, commodity, 0);
						//						System.out.println("origin:"+origin+", destination:"+destination+" mode: "+mode+" mode share is "+baseOD.getMode_share(origin, destination, mode)[commodity]);
					}
				}

				//5. distribution of the trade flows from centroid i to centroid j over different modes (0,..5) for commodity k	
				//the monetary values are stored in array based on different modes
				for (int mod=0; mod<modes; mod++)
				{
					//the trade flow is taken based on the year index but the mode share is not 
					//this is because the mode share will take into account the static changes that happen on the network at a certain year
					//or gradually across the span of several time periods
					double tradeWeight= baseOD.getWeightOD_year(origin, destination, commodity, yearIndex);
					double modeShare= baseOD.getMode_share(origin, destination, mod)[commodity];
					double weightByMode= modeShare*tradeWeight;
					
					//FIXME adjustment of volume for mode rail to reflect additional volume from and to ports
					if(mod==0)
					{
						weightByMode=weightByMode;
					}
					baseOD.setWeightOD(origin, destination, mod, commodity, weightByMode);

//					System.out.println("origin: "+origin+" destination: "+destination+" mode: "+mod+" commodity: "+commodity+
//							" mode share: "+modeShare+" trade: "+tradeWeight);

					//assigning the weight values by modes for the reference year
					if (yearIndex==0)
					{
						baseOD.setWeightODRef(origin, destination, mod, commodity, weightByMode);
					}
				}// mode share loop
			}//centroid destination loop
		}//centroid origin loop

		//returning the base OD to freight network for output file
		endTime=System.currentTimeMillis();
		processTime= endTime-startTime;
		System.out.println("Freight value to weight conversion for year: "+yearIndex+" is completed : "+processTime+" ms");
	}
}
