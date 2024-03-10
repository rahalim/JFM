package jfm.main;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class JFMMain 
{
	public static void main(String args[]) throws Exception{

		long startTime = System.currentTimeMillis();

		//needs to be updated
		String wd =  "C:/Users/halim_r/workspace/";
				
		JavaFreightModel model = new JavaFreightModel(wd);

		long endTime = System.currentTimeMillis();
		long computationTime=endTime-startTime;

		System.out.println("model run is completed, run time:"+computationTime +" ms");
	}
}
