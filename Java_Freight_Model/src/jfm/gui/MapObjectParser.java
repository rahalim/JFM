package jfm.gui;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import au.com.bytecode.opencsv.CSVReader;
import jfm.network.AbstractNode;


public class MapObjectParser {
	
	private static HashMap<Integer,AbstractNode> nodes = new HashMap<Integer,AbstractNode>(); 
	
	public MapObjectParser(String filename)
	{
		
	}
	
	public static HashMap<Integer, AbstractNode> readMapObjects(String dir)
	{
		
		try {
			CSVReader reader = new CSVReader(new FileReader(dir), ',','#', 1);
			String[] nextLine;
			

			while ((nextLine = reader.readNext()) != null) {
				
				int id = Integer.parseInt(nextLine[0]);
				String name = nextLine[1];
				double lat = Double.parseDouble(nextLine[2]);
				double lon = Double.parseDouble(nextLine[3]);
				double[] pop = new double[2];
				pop[0] = Double.parseDouble(nextLine[4]);
				pop[1] = Double.parseDouble(nextLine[5]);

				//				System.out.println("Object ID "+id+" "+"lat :"+lat+" "+"lon :"+lon+" pop 2015: "+pop2015+" pop2050: "+pop2050);	
				AbstractNode node = new AbstractNode(lat, lon,name, id);
				node.setCapacity(pop);
				nodes.put(id, node);	
			}
			reader.close();	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return nodes;
	}

}
