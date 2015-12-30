package simROHC;

import java.util.List;

/**
 * 
 * The summary of one ROHC session
 *
 */
public class SummarySession {
	double [] rewardInstant;
	double [] rewardLongTerm;
	int nIR;
	int nFO;
	int nSO;
	
	int nG;
	int nB;
	
	int [] lenTransmitted; // The cumulative number of bytes transmitted
	int [] lenReceived; // The cumulative number of payload bytes received
	
	public SummarySession(List<CompressorPOMDP.LogEntry> logCompressor, List<Boolean> logChannel, List<Decompressor.LogEntry> logDecompressor, double gamma, int lenHeaderIR, int lenHeaderFO, int lenHeaderSO, int lenPayload, int h) {
		
		int nPacket = logCompressor.size();
		rewardInstant = new double [nPacket];
		rewardLongTerm = new double [nPacket - h];
		
		lenTransmitted = new int [nPacket + 1];
		lenReceived = new int [nPacket + 1];
		
		assert(logDecompressor.size() == nPacket + 1 && logChannel.size() == nPacket + 1);
		
		int lenIR = lenHeaderIR + lenPayload;
		int lenFO = lenHeaderFO + lenPayload;
		int lenSO = lenHeaderSO + lenPayload;
		
		for (int iPacket = 0; iPacket < nPacket; iPacket++) {
			switch (logCompressor.get(iPacket).typePacket) {
				case 0: nIR++; lenTransmitted[iPacket + 1] = lenTransmitted[iPacket] + lenIR; break;
				case 1: nFO++; lenTransmitted[iPacket + 1] = lenTransmitted[iPacket] + lenFO; break;
				default: nSO++; lenTransmitted[iPacket + 1] = lenTransmitted[iPacket] + lenSO;
			}
			
			if (logChannel.get(iPacket)) {
				nG++;
				switch (logDecompressor.get(iPacket).state) { // The decompressor's state before transmitting the iPacket-th packet
					case NC: {
						if (logCompressor.get(iPacket).typePacket == 0) {
							lenReceived[iPacket + 1] = lenReceived[iPacket] + lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenIR;
						} else {
							lenReceived[iPacket + 1] = lenReceived[iPacket];
							rewardInstant[iPacket] = 0.0;
						}
						break;
					}
					case SC: {
						if (logCompressor.get(iPacket).typePacket == 0) {
							lenReceived[iPacket + 1] = lenReceived[iPacket] + lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenIR;
						} else if (logCompressor.get(iPacket).typePacket == 1) {
							lenReceived[iPacket + 1] = lenReceived[iPacket] + lenPayload;
							rewardInstant[iPacket] = ((double) lenPayload) / lenFO;
						}  else {
							lenReceived[iPacket + 1] = lenReceived[iPacket];
							rewardInstant[iPacket] = 0.0;
						}
						break;
					}
					default: {
						lenReceived[iPacket + 1] = lenReceived[iPacket] + lenPayload;
						switch (logCompressor.get(iPacket).typePacket) {
							case 0: rewardInstant[iPacket] = ((double) lenPayload) / lenIR; break;
							case 1: rewardInstant[iPacket] = ((double) lenPayload) / lenFO; break;
							default : rewardInstant[iPacket] = ((double) lenPayload) / lenSO;
						}
					}
				}
			} else {
				nB++;
				lenReceived[iPacket + 1] = lenReceived[iPacket];
				rewardInstant[iPacket] = 0.0;
			}
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
		
		for (int iPacket = 0; iPacket < lenTransmitted.length; iPacket++) {
			lenTransmitted[iPacket] += summary.lenTransmitted[iPacket];
			lenReceived[iPacket] += summary.lenReceived[iPacket];
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
		
		for (int iPacket = 0; iPacket < lenTransmitted.length; iPacket++) {
			lenTransmitted[iPacket] /= nRun;
			lenReceived[iPacket] /= nRun;
		}
	}
	
	void plotPerformance() {
		//TODO: plot how do the rewards (instant and long term) and number of bytes (transmitted and received as payload) vary over time
	}
}
