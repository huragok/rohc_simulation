package simROHC;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;

/**
 * 
 * The summary of one ROHC session
 *
 */
public class SummarySession {
	double [] efficiency;
	double nIR;
	double nFO;
	double nSO;
	
	double nG;
	double nB;
	
	public SummarySession(List<Integer> logPacketType, List<Boolean> logChannel, List<Decompressor.LogEntry> logDecompressor, int lenHeaderIR, int lenHeaderFO, int lenHeaderSO, int lenPayload) {
		
		int nPacket = logPacketType.size();
		efficiency = new double [nPacket];
		
		int nByteTransmitted = 0; // The cumulative number of bytes transmitted
		int nByteReceived = 0; // The cumulative number of payload bytes received
		
		assert(logDecompressor.size() == nPacket + 1 && logChannel.size() == nPacket + 1);
		
		int lenIR = lenHeaderIR + lenPayload;
		int lenFO = lenHeaderFO + lenPayload;
		int lenSO = lenHeaderSO + lenPayload;
		
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			switch (logPacketType.get(iPacket)) {
				case 0: nIR++; nByteTransmitted += lenIR; break;
				case 1: nFO++; nByteTransmitted += lenFO; break;
				default: nSO++; nByteTransmitted += lenSO;
			}
			
			if (logChannel.get(iPacket)) {
				nG++;
				switch (logDecompressor.get(iPacket).state) { // The decompressor's state before transmitting the iPacket-th packet
					case NC: {
						if (logPacketType.get(iPacket) == 0) {
							nByteReceived += lenPayload;
						} 
						break;
					}
					case SC: {
						if (logPacketType.get(iPacket) <= 1) {
							nByteReceived += lenPayload;
						}
						break;
					}
					default: {
						nByteReceived += lenPayload;
					}
				}
			} else {
				nB++;
			}
			efficiency[iPacket] = ((double)nByteReceived) / nByteTransmitted;
		}		
	}
	
	public SummarySession(int nPacket) {
		efficiency = new double [nPacket];
		nIR = 0;
		nFO = 0;
		nSO = 0;
		
		nG = 0;
		nB = 0;
	}
	
	public String toString() {
		StringBuilder output = new StringBuilder();
		output.append("***** Summary of ROHC session *****\n");
		output.append("Total number of TBs: " + (nIR + nFO + nSO) + "\n");
		output.append("Type of TBs:\n");
		output.append(" - IR:" + nIR + "\n");
		output.append(" - FO:" + nFO + "\n");
		output.append(" - SO:" + nSO + "\n");
		output.append("Channel states:\n");
		output.append(" - Good:" + nG + "\n");
		output.append(" - Bad:" + nB + "\n");
		return new String(output);
	}
	
	void sum(SummarySession summary) {
		
		nIR += summary.nIR;
		nFO += summary.nFO;
		nSO += summary.nSO;
		
		nG += summary.nG;
		nB += summary.nB;
		
		for (int iPacket = 0; iPacket < efficiency.length; iPacket++) {
			efficiency[iPacket] += summary.efficiency[iPacket];
		}
	}
	
	void normalize(int nRun) {
		nIR /= nRun;
		nFO /= nRun;
		nSO /= nRun;
		
		nG /= nRun;
		nB /= nRun;
		
		for (int iPacket = 0; iPacket < efficiency.length; iPacket++) {
			efficiency[iPacket] /= nRun;
		}
	}
	
	static void plotPerformance(SummarySession [] sessions, String [] sessionNames, int lenHeaderIR, int lenHeaderFO, int lenHeaderSO, int lenPayload, double pG) throws IOException {
		if (sessions.length == 0) return;
		
		assert(sessions.length == sessionNames.length);
		int nSession = sessions.length;
		int nPacket = sessions[0].efficiency.length;
		DefaultXYDataset datasetEfficiency = new DefaultXYDataset();
		double maxEfficiency = 0.0;
		double efficiencyIR = pG * (double)lenPayload / (lenPayload + lenHeaderIR); // The efficiency of transmitting IR packet only, considering the lossy channel
		double efficiencyFO = pG * (double)lenPayload / (lenPayload + lenHeaderFO); // The efficiency of transmitting FO packet only, considering the lossy channel but not the unsynchronized context 
		double efficiencySO = pG * (double)lenPayload / (lenPayload + lenHeaderSO); // The efficiency of transmitting SO packet only, considering the lossy channel but not the unsynchronized context 
		

		for (int iSession = 0; iSession < nSession; iSession++) {
			double [][] efficiency = new double [2][nPacket];
			
			for (int iPacket = 0; iPacket < nPacket; iPacket++) {
				efficiency[0][iPacket] = iPacket;
				efficiency[1][iPacket] = sessions[iSession].efficiency[iPacket];
				maxEfficiency = (maxEfficiency > efficiency[1][iPacket] ? maxEfficiency : efficiency[1][iPacket]);
			}
			
			datasetEfficiency.addSeries(sessionNames[iSession], efficiency);
		}
		
		double [][] efficiency = new double [2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			efficiency[0][iPacket] = iPacket;
			efficiency[1][iPacket] = efficiencyIR;
		}
		datasetEfficiency.addSeries("IR", efficiency);
		
		efficiency = new double [2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			efficiency[0][iPacket] = iPacket;
			efficiency[1][iPacket] = efficiencyFO;
		}
		datasetEfficiency.addSeries("FO", efficiency);
		
		efficiency = new double [2][nPacket];
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			efficiency[0][iPacket] = iPacket;
			efficiency[1][iPacket] = efficiencySO;
		}
		datasetEfficiency.addSeries("SO", efficiency);
		
		Font fontGeneral = new Font("Dialog", Font.PLAIN, 20); // Set all fontsize to 20
		
		// The common x-axis
		NumberAxis xAxis = new NumberAxis("Packet");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        xAxis.setTickLabelFont(fontGeneral);
        xAxis.setLabelFont(fontGeneral);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(xAxis);
             
        // Subplot 2: efficiency (cumulative)
        NumberAxis yAxisEfficiency = new NumberAxis("Efficiency");
        yAxisEfficiency.setTickUnit(new NumberTickUnit(0.1));
        yAxisEfficiency.setLabelFont(fontGeneral);
        yAxisEfficiency.setTickLabelFont(fontGeneral);
        yAxisEfficiency.setRange(0, 1);
        
        XYItemRenderer rendererEfficiency = new XYLineAndShapeRenderer();
        rendererEfficiency.setSeriesStroke(0, new BasicStroke(2f));
        rendererEfficiency.setSeriesStroke(1, new BasicStroke(2f));
        XYPlot subplotEfficiency = new XYPlot(datasetEfficiency, null, yAxisEfficiency, rendererEfficiency);
        
        LegendTitle ltEfficiency = new LegendTitle(subplotEfficiency);
        ltEfficiency.setItemFont(fontGeneral);
        ltEfficiency.setFrame(new BlockBorder(Color.white));
        ltEfficiency.setPosition(RectangleEdge.BOTTOM);
        XYTitleAnnotation annotationEfficiency= new XYTitleAnnotation(0.98, 0.02, ltEfficiency, RectangleAnchor.BOTTOM_RIGHT);
        subplotEfficiency.addAnnotation(annotationEfficiency);
        
        plot.add(subplotEfficiency, 1);
        
        // Create the jfreechart 
        JFreeChart chart = new JFreeChart("", plot);
        chart.removeLegend();
        chart.setBackgroundPaint(Color.white);
        
        File output = new File("reward.jpg"); 
	    ChartUtilities.saveChartAsJPEG(output, chart, 1920, 1080);
	}
}
