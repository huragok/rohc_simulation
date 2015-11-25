package simROHC;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.List;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;

/**
* A <code>Simulator</code> simulates a simple ROHC model in LTE protocol stack and Gilbert-Elliot channel model.
*
* @author Wenhao Wu wnhwu@ucdavis.edu
*/
public class Simulator {
	
	public static void main (String [] args) throws Exception {
		int W = 8;
		int lB = 8;
		double eps = 0.2;
		double pFA = 0.1;
		double pMD = 0.1;
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
		
		XYZDataset datasetBelief = createBeliefDataSet(compressor.log);
		XYDataset datasetState = createStateDataSet(channel.log, decompressor.log);
		XYDataset datasetChannel = createChannelDataSet(channel.log, compressor.log);
		XYDataset datasetPacket =  createTypePacketDataSet(compressor.log);
		
		JFreeChart chart = createChart(W, datasetBelief, datasetState, datasetChannel, datasetPacket);
		
		int width = 1920; // Width of the image
	    int height = 1080; // Height of the image
	      
		File output = new File("result.jpg"); 
	    ChartUtilities.saveChartAsJPEG(output, chart, width, height);
	    System.out.println("Simulation completed");
	}
	
	/**
	 * Create the dataset used to generate the heatmap representing the compressor's belief on the system's states
	 * @param log
	 * @return 
	 */
	static XYZDataset createBeliefDataSet(List<CompressorPOMDP.LogEntry> log) {
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
		DefaultXYZDataset beliefHistory = new DefaultXYZDataset();
		beliefHistory.addSeries("Series0", logBelief);
		return beliefHistory;
	}
	
	/**
	 * Create the dataset corresponding to the actual system's states
	 * @param logChannel
	 * @param logDecompressor
	 * @return
	 */
	static XYDataset createStateDataSet(List<Boolean> logChannel, List<Decompressor.LogEntry> logDecompressor) {
		int nPacket = logDecompressor.size();
		
		double [][] states = new double[2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			states[0][iPacket] = iPacket;
			switch (logDecompressor.get(iPacket).state) {
				case NC: {
					states[1][iPacket] = logChannel.get(iPacket) ? 1 : 0;
					break;
				}
				case SC: {
					states[1][iPacket] = logChannel.get(iPacket) ? 3 : 2;
					break;
				}
				default: {
					states[1][iPacket] = logDecompressor.get(iPacket).w + 4;
				}
			}
		}
	
		DefaultXYDataset stateHistory = new DefaultXYDataset();
		stateHistory.addSeries("Series0", states);
		return stateHistory;
	}
	
	/**
	 * Create the dataset representing the actual and the observed channel states
	 * @param logChannel
	 * @param logCompressor
	 * @return
	 */
	static XYDataset createChannelDataSet(List<Boolean> logChannel, List<CompressorPOMDP.LogEntry> logCompressor) {
		int nPacket = logCompressor.size();
		
		double [][] stateChannelActual = new double[2][nPacket];
		double [][] stateChannelObs = new double[2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			stateChannelActual[0][iPacket] = iPacket;
			stateChannelActual[1][iPacket] = logChannel.get(iPacket) ? 1 : 0;
			
			stateChannelObs[0][iPacket] = iPacket;
			stateChannelObs[1][iPacket] = logCompressor.get(iPacket).channelObs ? 1 : 0;
		}
	
		DefaultXYDataset stateChannel = new DefaultXYDataset();
		stateChannel.addSeries("Actual", stateChannelActual);
		stateChannel.addSeries("Observed", stateChannelObs);
		return stateChannel;
	}
	
	/**
	 * Create the dataset representing the type of packets transmitted
	 * @param logCompressor
	 * @return
	 */
	static XYDataset createTypePacketDataSet(List<CompressorPOMDP.LogEntry> logCompressor) {
		int nPacket = logCompressor.size();
		
		double [][] packets = new double[2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			packets[0][iPacket] = iPacket;
			packets[1][iPacket] = logCompressor.get(iPacket).typePacket;

		}
	
		DefaultXYDataset action = new DefaultXYDataset();
		action.addSeries("Series0", packets);
		return action;
	}
	
	/**
	 * Create and save a jfreechart
	 * @param W
	 * @param datasetBelief
	 * @param datasetState
	 * @param datasetChannel
	 * @param datasetPacket
	 * @return
	 */
	public static JFreeChart createChart(int W, XYZDataset datasetBelief, XYDataset datasetState, XYDataset datasetChannel, XYDataset datasetPacket) {
		Font fontGeneral = new Font("Dialog", Font.PLAIN, 20); // Set all fontsize to 20
		
		// The common x-axis
		NumberAxis xAxis = new NumberAxis("Packet");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        xAxis.setTickLabelFont(fontGeneral);
        xAxis.setLabelFont(fontGeneral);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(xAxis);
        
        // Supblot 1
        //// y-axis 1: the heatmap representing the decompressor's belief
        String [] tickState = new String [W + 4];
        tickState[0] = "NCB"; tickState[1] = "NCG"; tickState[2] = "SCB"; tickState[3] = "SCG"; 
        for (int w = 0; w < W; w++) tickState[w + 4] = "FC" + Integer.toString(w);
        SymbolAxis yAxisState = new SymbolAxis("State", tickState);
        yAxisState.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxisState.setLowerMargin(0.0);
        yAxisState.setUpperMargin(0.0);
        yAxisState.setTickLabelFont(fontGeneral);
        yAxisState.setLabelFont(fontGeneral);
        
        XYBlockRenderer rendererBelief = new XYBlockRenderer();
        LookupPaintScale paintScale = createPaintScale(256);
        rendererBelief.setPaintScale(paintScale);
        
        XYPlot subplotState = new XYPlot(datasetBelief, null, yAxisState, rendererBelief); // The system state
        
        NumberAxis scaleAxis = new NumberAxis("Belief"); // The color scale of the heat map
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
        
        //// y-axis 2: the lines representing the actual system's state
        XYItemRenderer rendererState = new XYLineAndShapeRenderer();
        rendererState.setSeriesPaint(0, Color.black);
        rendererState.setSeriesStroke(0, new BasicStroke(2f));
        subplotState.setDataset(1, datasetState);
        subplotState.setRenderer(1, rendererState);
        subplotState.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD); // Order
        subplotState.setAxisOffset(new RectangleInsets(5, 5, 5, 5));
        
        plot.add(subplotState, 4);
        
        // Subplot 2: the actual and observe channel
        SymbolAxis yAxisChannel = new SymbolAxis("Channel", new String [] {"B", "G"});
        yAxisChannel.setTickLabelFont(fontGeneral);
        yAxisChannel.setLabelFont(fontGeneral);
        XYItemRenderer rendererChannel = new XYLineAndShapeRenderer();
        rendererChannel.setSeriesStroke(0, new BasicStroke(2f));
        rendererChannel.setSeriesStroke(1, new BasicStroke(2f));
        XYPlot subplotChannel = new XYPlot(datasetChannel, null, yAxisChannel, rendererChannel); // The channel state/obs
        
        LegendTitle lt = new LegendTitle(subplotChannel);
        lt.setItemFont(fontGeneral);
        lt.setFrame(new BlockBorder(Color.white));
        lt.setPosition(RectangleEdge.BOTTOM);
        XYTitleAnnotation annotation= new XYTitleAnnotation(0.98, 0.02, lt, RectangleAnchor.BOTTOM_RIGHT);
        subplotChannel.addAnnotation(annotation);
        
        plot.add(subplotChannel, 1);
        
        // Subplot 3: the type of packets transmitted (Actions)
        SymbolAxis yAxisPacket = new SymbolAxis("Packet Type", new String [] {"IR", "FO", "SO"});
        yAxisPacket.setTickLabelFont(fontGeneral);
        yAxisPacket.setLabelFont(fontGeneral);
        XYItemRenderer rendererPacket = new XYLineAndShapeRenderer();
        rendererPacket.setSeriesStroke(0, new BasicStroke(2f));
        rendererPacket.setSeriesPaint(0, Color.black);
        XYPlot subplotPacket = new XYPlot(datasetPacket, null, yAxisPacket, rendererPacket); // The packet
        plot.add(subplotPacket, 1);
        
        // Create the jfreechart 
        JFreeChart chart = new JFreeChart("", plot);
        chart.removeLegend();
        chart.addSubtitle(legend); // add the colorscale
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