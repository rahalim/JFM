package jfm.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import jfm.network.MultiModalNetworkCostNoSQL;
import processing.core.PApplet;

//object created to visualize map directly from the model while on the run
public class JFMVisualizationMap extends Frame {

	// shortcut key to close this window/frame
	private static final int kControlX = 88;
	private PApplet applet;
	private MultiModalNetworkCostNoSQL network;

	public JFMVisualizationMap (MultiModalNetworkCostNoSQL network,int mode) {
		//set frame's title
		super("Visualization Frame Global Maritime Shipping Network");
		//add menu
		this.addMenu();

		//adding the network from the model
		this.network= network;
		
		//add the drawing applet
		this.addApplet(mode);

		//add windows listener
		this.addWindowListener(new WindowHandler());
		//set frame size
		this.setSize(1700,1050);
//		this.setSize(1400,800);
		//make this frame visible
		this.setVisible(true);
	}
	
	//constructor for IRU maps that have multiple thresholds
	public JFMVisualizationMap (MultiModalNetworkCostNoSQL network,int mode, double threshold) {
		//set frame's title
		super("Visualization Frame International Freight Transport Network");
		//add menu
		this.addMenu();

		//adding the network from the model
		this.network= network;
		
		//add the drawing applet
		this.addApplet(mode, threshold);

		//add windows listener
		this.addWindowListener(new WindowHandler());
		//set frame size
		this.setSize(1700,1050);
//		this.setSize(1400,800);
		//make this frame visible
		this.setVisible(true);
	}


	/**
	 * this method adds menu to the bezier frame
	 */
	private void addMenu()
	{
		//Add menu bar to the frame
		MenuBar menuBar = new MenuBar();
		Menu file = new Menu("File");

		//Add menu items for the file menu
		file.add(new MenuItem("Open")).addActionListener(new WindowHandler());
		file.add(new MenuItem("Save")).addActionListener(new WindowHandler());
		file.add(new MenuItem("Exit Visualization Frame", new MenuShortcut(kControlX))).addActionListener(new WindowHandler());
		file.add(new MenuItem("Clear")).addActionListener(new WindowHandler());

		menuBar.add(file);
		if(this.getMenuBar()==null)
		{
			this.setMenuBar(menuBar);
		}
	}//addMenu()

	/**
	  This method adds an applet to the bezier frame for drawing the bezier curve
	 */
	private void addApplet(int mode)
	{
		this.applet = new GlobalMaritimeShipping(network, mode);
//		this.applet = new EurasiaMap(network, mode);
		this.add(applet, BorderLayout.CENTER); 	
		
		applet.init();
//		applet.start();
	}//end of addApplet();
	
	private void addApplet(int mode, double threshold)
	{
		this.applet = new GlobalMaritimeShipping(network, mode, threshold);
//		this.applet = new EurasiaMap(network, mode);
		this.add(applet, BorderLayout.CENTER); 	
		
		applet.init();
//		applet.start();
	}//end of addApplet();

	private void disposeFrame()
	{
		this.dispose();
	}

	private class WindowHandler extends WindowAdapter implements ActionListener
	{

		public void windowClosing(WindowEvent e)
		{
			disposeFrame();
		}

		public void actionPerformed(ActionEvent e)
		{
			//check to see if the action command is equal to exit
			if(e.getActionCommand().equalsIgnoreCase("Exit Visualization Frame"))
			{
				disposeFrame();
			}
			else if(e.getActionCommand().equalsIgnoreCase("Open"))
			{
				JFileChooser jfc = new JFileChooser();
				FileNameExtensionFilter filter = new FileNameExtensionFilter("JPG & GIF Images", "jpg", "gif");
				jfc.setFileFilter(filter);
				int result = jfc.showOpenDialog(JFMVisualizationMap.this);
				if(result == JFileChooser.APPROVE_OPTION)
				{
					File file = jfc.getSelectedFile();
					try {
						Image img = ImageIO.read(file);
						//							applet.repaint();
					} 
					catch (Exception a) {
						JOptionPane.showMessageDialog(JFMVisualizationMap.this,a.getMessage(),"File error",JOptionPane.ERROR_MESSAGE);
					}
				}
			}

			else if (e.getActionCommand().equalsIgnoreCase("Save"))
			{
				JOptionPane.showMessageDialog(null, "Do not forget to enter the filename extension (.jpeg,.gif)" , "A Geometric Drawing Tool", JOptionPane.PLAIN_MESSAGE);
				JFileChooser jfc = new JFileChooser();
				int result = jfc.showSaveDialog(JFMVisualizationMap.this);
				if(result == JFileChooser.APPROVE_OPTION) 
					try {
						BufferedImage image = new BufferedImage(applet.getWidth(), applet.getHeight(), BufferedImage.TYPE_INT_RGB);
						Graphics2D g = image.createGraphics();
						g.setBackground(Color.WHITE);
						applet.paint(g);
						g.dispose();
						File saveLocation =jfc.getSelectedFile();
						ImageIO.write(image, "JPG", saveLocation);

					}
				catch (Exception a) 
				{
					JOptionPane.showMessageDialog(JFMVisualizationMap.this, a.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			else if (e.getActionCommand().equalsIgnoreCase("Clear"))
			{
				applet.redraw();
			}
		}
	}

	public PApplet getApplet() {

		return applet;
	}
}
