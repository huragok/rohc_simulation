package simROHC;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;

/**
* A <code>Simulator</code> simulates a simple ROHC model in LTE protocol stack and Gilbert-Elliot channel model.
*
* @author Wenhao Wu wnhwu@ucdavis.edu
*/
public class Simulator {
	
	public static void main (String [] args) throws Exception {
		int W = 10;
		int lB = 8;
		double eps = 0.2;
		double pFA = 0.2;
		double pMD = 0.2;
		String filename = "out.policy";
		int N = 200; // Number of packets to transmit
		
		// Create the components of the simulator
		Decompressor decompressor = new Decompressor(W);
		Channel channel = new Channel(eps, lB);
		CompressorPOMDP.ChannelEstimator channelEstimator = new CompressorPOMDP.ChannelEstimator(channel, pFA, pMD);
		CompressorPOMDP compressor = new CompressorPOMDP(W, channel.pBG, channel.pGB, channelEstimator, filename);
		
		// Start the simulation
		for (int n = 0; n < N; n++) {
			int typePacket = compressor.transmit(); // Compressor takes an action by transmitting a packet and updates its own state
			decompressor.next(channel.isGood, typePacket); // Decompressor update its state according to the actual channel state and the packet (if received)
			channel.next(); // Update the channel state
		}
		
		
		//System.out.println(channel.log);
		
		XYZDataset dataset =  createBeliefDataSet(compressor.log);
		JFreeChart beliefChart = createBeliefChart(dataset);
		int width = 1920; /* Width of the image */
	    int height = 1080; /* Height of the image */ 
	      
		File output = new File( "Belief.jpg" ); 
	    ChartUtilities.saveChartAsJPEG(output, beliefChart, width, height);
	    System.out.println("Simulation completed");
	}
	
	public static XYZDataset createBeliefDataSet(List<CompressorPOMDP.LogEntry> log) {
		int nPacket = log.size();
		int nState = log.get(0).belief.length;
		double [][] logBelief = new double[3][nPacket * nState];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			for (int iState = 0; iState < nState; iState++) {
				int idx = iPacket * nState + iState;
				logBelief[0][idx] = iPacket;
				logBelief[1][idx] = iState;
				logBelief[2][idx] = log.get(iPacket).belief.get(iState);
			}
		}
		//System.out.println(Arrays.toString(logBelief[0]));
		//System.out.println(Arrays.toString(logBelief[1]));
		//System.out.println(Arrays.toString(logBelief[2]));
		DefaultXYZDataset beliefHistory = new DefaultXYZDataset();
		beliefHistory.addSeries("Series0", logBelief);
		return beliefHistory;
	}
	
	public static JFreeChart createBeliefChart(XYZDataset dataset) {
		Font fontGeneral = new Font("Dialog", Font.PLAIN, 20);
		
		NumberAxis xAxis = new NumberAxis("Packet");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        xAxis.setTickLabelFont(fontGeneral);
        xAxis.setLabelFont(fontGeneral);
        
        NumberAxis yAxis = new NumberAxis("State");
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxis.setLowerMargin(0.0);
        yAxis.setUpperMargin(0.0);
        yAxis.setTickLabelFont(fontGeneral);
        yAxis.setLabelFont(fontGeneral);
        
        XYBlockRenderer renderer = new XYBlockRenderer();
        LookupPaintScale paintScale = createPaintScale(256);
        renderer.setPaintScale(paintScale);
        
        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);

        //plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5, 5, 5, 5));
        JFreeChart chart = new JFreeChart("", plot);
        chart.removeLegend();
        NumberAxis scaleAxis = new NumberAxis("Belief");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);
        scaleAxis.setLabelFont(fontGeneral);
        scaleAxis.setTickLabelFont(fontGeneral);
        scaleAxis.setRange(0, 1);
        
        PaintScaleLegend legend = new PaintScaleLegend(paintScale, scaleAxis);
        legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        legend.setAxisOffset(5.0);
        legend.setMargin(new RectangleInsets(5, 5, 5, 5));
        legend.setPadding(new RectangleInsets(10, 10, 10, 10));
        legend.setStripWidth(10);
        legend.setPosition(RectangleEdge.RIGHT);
        chart.addSubtitle(legend);
        chart.setBackgroundPaint(Color.white);
        
        return chart;
	}
	
	static LookupPaintScale createPaintScale(int numColor) {
		LookupPaintScale paintScale = new LookupPaintScale(0, 1, Color.gray);
		int L = numColor / 4;
		float stepVal = 1.0f / (L + 1);
		float stepKey = 0.25f / (L + 1);
		for (int l = 0; l < L; l++) { 
			paintScale.add(1 - l * stepKey, new Color(1.0f, stepVal * l, 0.0f)); // Red to yellow
			paintScale.add(0.75 - l * stepKey, new Color(1.0f - stepVal * l, 1.0f, 0)); // yellow to green
			paintScale.add(0.5 - l * stepKey, new Color(0.0f, 1.0f, stepVal * l)); // green to lightblue
			paintScale.add(0.25 - l * stepKey, new Color(0.0f, 1.0f - stepVal * l, 1.0f)); // lightblue to blue
		}
		
		paintScale.add(0, new Color(0.0f, 0.0f, 1.0f));
		return paintScale;
	}
}