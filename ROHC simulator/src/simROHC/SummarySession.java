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
	double [] rewardInstant;
	double [] rewardLongTerm;
	double [] efficiency;
	double nIR;
	double nFO;
	double nSO;
	
	double nG;
	double nB;
	
	public SummarySession(List<CompressorPOMDP.LogEntry> logCompressor, List<Boolean> logChannel, List<Decompressor.LogEntry> logDecompressor, double gamma, int lenHeaderIR, int lenHeaderFO, int lenHeaderSO, int lenPayload, int h) {
		
		int nPacket = logCompressor.size();
		rewardInstant = new double [nPacket];
		rewardLongTerm = new double [nPacket - h];
		efficiency = new double [nPacket];
		
		int nByteTransmitted = 0; // The cumulative number of bytes transmitted
		int nByteReceived = 0; // The cumulative number of payload bytes received
		
		assert(logDecompressor.size() == nPacket + 1 && logChannel.size() == nPacket + 1);
		
		int lenIR = lenHeaderIR + lenPayload;
		int lenFO = lenHeaderFO + lenPayload;
		int lenSO = lenHeaderSO + lenPayload;
		
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			switch (logCompressor.get(iPacket).typePacket) {
				case 0: nIR++; nByteTransmitted += lenIR; break;
				case 1: nFO++; nByteTransmitted += lenFO; break;
				default: nSO++; nByteTransmitted += lenSO;
			}
			
			if (logChannel.get(iPacket)) {
				nG++;
				switch (logDecompressor.get(iPacket).state) { // The decompressor's state before transmitting the iPacket-th packet
					case NC: {
						if (logCompressor.get(iPacket).typePacket == 0) {
							nByteReceived += lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenIR;
						} else {
							rewardInstant[iPacket] = 0.0;
						}
						break;
					}
					case SC: {
						if (logCompressor.get(iPacket).typePacket == 0) {
							nByteReceived += lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenIR;
						} else if (logCompressor.get(iPacket).typePacket == 1) {
							nByteReceived += lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenFO;
						}  else {
							rewardInstant[iPacket] = 0.0;
						}
						break;
					}
					default: {
						nByteReceived += lenPayload;
						switch (logCompressor.get(iPacket).typePacket) {
							case 0: rewardInstant[iPacket] = ((double) lenPayload) / lenIR; break;
							case 1: rewardInstant[iPacket] = ((double) lenPayload) / lenFO; break;
							default : rewardInstant[iPacket] = ((double) lenPayload) / lenSO;
						}
					}
				}
			} else {
				nB++;
				rewardInstant[iPacket] = 0.0;
			}
			efficiency[iPacket] = ((double)nByteReceived) / nByteTransmitted;
		}
		
		// Evaluate the long term reward
		
		//// Evaluate the first long term reward
		double weight = 1;
		double rewardTmp = 0;
		for (int iPacket = 0; iPacket <= h; iPacket++) {
			rewardTmp += weight * rewardInstant[iPacket];
			weight *= gamma;
		}
		rewardLongTerm[0] = rewardTmp;
		
		// now weight = gamma ^ (h + 1)
		for (int iPacket = h + 1; iPacket < nPacket; iPacket++) {
			rewardTmp = (rewardTmp + weight * rewardInstant[iPacket] - rewardInstant[iPacket - h - 1]) / gamma;
			rewardLongTerm[iPacket - h] = rewardTmp;
		}
	}
	
	public SummarySession(int nPacket, int h) {
		rewardInstant = new double [nPacket];
		rewardLongTerm = new double [nPacket - h];
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
		assert(rewardInstant.length == summary.rewardInstant.length);
		assert(rewardLongTerm.length == summary.rewardLongTerm.length);
		
		nIR += summary.nIR;
		nFO += summary.nFO;
		nSO += summary.nSO;
		
		nG += summary.nG;
		nB += summary.nB;
		
		for (int iPacket = 0; iPacket < rewardInstant.length; iPacket++) {
			rewardInstant[iPacket] += summary.rewardInstant[iPacket];
		}
		
		for (int iPacket = 0; iPacket < rewardLongTerm.length; iPacket++) {
			rewardLongTerm[iPacket] += summary.rewardLongTerm[iPacket];
		}
		
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
		
		for (int iPacket = 0; iPacket < rewardInstant.length; iPacket++) {
			rewardInstant[iPacket] /= nRun;
		}
		
		for (int iPacket = 0; iPacket < rewardLongTerm.length; iPacket++) {
			rewardLongTerm[iPacket] /= nRun;
		}
		
		for (int iPacket = 0; iPacket < efficiency.length; iPacket++) {
			efficiency[iPacket] /= nRun;
		}
	}
	
	static void plotPerformance(SummarySession [] sessions, int lenHeaderIR, int lenHeaderFO, int lenHeaderSO, int lenPayload, double pG) throws IOException {
		if (sessions.length == 0) return;
		
		int nSession = sessions.length;
		DefaultXYDataset datasetReward = new DefaultXYDataset();
		DefaultXYDataset datasetEfficiency = new DefaultXYDataset();
		double maxReward = 0.0;
		double maxEfficiency = 0.0;
		double efficiencyIR = pG * (double)lenPayload / (lenPayload + lenHeaderIR); // The efficiency of transmitting IR packet only, considering the lossy channel
		double efficiencyFO = pG * (double)lenPayload / (lenPayload + lenHeaderFO); // The efficiency of transmitting FO packet only, considering the lossy channel but not the unsynchronized context 
		double efficiencySO = pG * (double)lenPayload / (lenPayload + lenHeaderSO); // The efficiency of transmitting SO packet only, considering the lossy channel but not the unsynchronized context 
		
		int nPacket = sessions[0].rewardInstant.length;
		int h = nPacket - sessions[0].rewardLongTerm.length;
		for (int iSession = 0; iSession < nSession; iSession++) {
			double [][] rewardLongTerm = new double[2][nPacket - h];
			double [][] efficiency = new double [2][nPacket - h];
			
			for (int iPacket = 0; iPacket < nPacket - h; iPacket++) {
				rewardLongTerm[0][iPacket] = iPacket;
				rewardLongTerm[1][iPacket] = sessions[iSession].rewardLongTerm[iPacket];
				maxReward = (maxReward > rewardLongTerm[1][iPacket] ? maxReward : rewardLongTerm[1][iPacket]);
				
				efficiency[0][iPacket] = iPacket;
				efficiency[1][iPacket] = sessions[iSession].efficiency[iPacket];
				maxEfficiency = (maxEfficiency > efficiency[1][iPacket] ? maxEfficiency : efficiency[1][iPacket]);
			}
			
			
			datasetReward.addSeries("Session " + iSession, rewardLongTerm);
			datasetEfficiency.addSeries("Session " + iSession, efficiency);
		}
		
		double [][] efficiency = new double [2][nPacket - h];
		for (int iPacket = 0; iPacket < nPacket - h; iPacket++) {
			efficiency[0][iPacket] = iPacket;
			efficiency[1][iPacket] = efficiencyIR;
		}
		datasetEfficiency.addSeries("IR", efficiency);
		
		efficiency = new double [2][nPacket - h];
		for (int iPacket = 0; iPacket < nPacket - h; iPacket++) {
			efficiency[0][iPacket] = iPacket;
			efficiency[1][iPacket] = efficiencyFO;
		}
		datasetEfficiency.addSeries("FO", efficiency);
		
		efficiency = new double [2][nPacket - h];
		for (int iPacket = 0; iPacket < nPacket - h; iPacket++) {
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
        
        // Subplot 1: long term reward
        NumberAxis yAxisReward = new NumberAxis("Reward");
        yAxisReward.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxisReward.setLabelFont(fontGeneral);
        yAxisReward.setTickLabelFont(fontGeneral);
        yAxisReward.setRange(0, ((int)(maxReward / 5) + 1) * 5.0);
        
        XYItemRenderer rendererReward = new XYLineAndShapeRenderer();
        rendererReward.setSeriesStroke(0, new BasicStroke(2f));
        rendererReward.setSeriesStroke(1, new BasicStroke(2f));
        XYPlot subplotReward = new XYPlot(datasetReward, null, yAxisReward, rendererReward);
        
        LegendTitle lt = new LegendTitle(subplotReward);
        lt.setItemFont(fontGeneral);
        lt.setFrame(new BlockBorder(Color.white));
        lt.setPosition(RectangleEdge.BOTTOM);
        XYTitleAnnotation annotation= new XYTitleAnnotation(0.98, 0.02, lt, RectangleAnchor.BOTTOM_RIGHT);
        subplotReward.addAnnotation(annotation);
        
        //plot.add(subplotReward, 1);
        
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
