package simROHC;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jblas.DoubleMatrix;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 
 * The cross-layer compressor based on the Partial Observable Markov Decision Process (POMDP) model
 *
 */
public class CompressorPOMDP implements Compressor {
	
	/**
	 * 
	 * The compressor can only observe ROHC channel state via a imperfect channel estimator
	 *
	 */
	static class ChannelEstimator {
		/** The channel being observed. */
		private final Channel channel;
		/** The false alarm probability (the channel is good but estimation is bad). */
		final double pFA;
		/** The miss detection probability (the channel is bad but estimation is good). */
		final double pMD;
		
		/** Create a channel estimator and associate with a Channel object. */
		public ChannelEstimator(Channel channel, double pFA, double pMD) {
			this.channel = channel;
			this.pFA = pFA;
			this.pMD = pMD;
		}
		
		/** Estimate the current channel state. */
		public boolean getChannelEst() {
			double tmp = Math.random();
			if (channel.getChannelState()) {
				return tmp > pFA;
			} else {
				return tmp < pMD;
			}
		};
	}
	
	/** The POMDP compressor has knowledge on the WLSB capacity. */
	final int W;
	/** The POMDP compressor has knowledge on the channel estimator's performance. */
	final double pFA, pMD;
	/** The POMDP compressor has knowledge on the G-E channel characteristics. */
	final double pBG, pGB;
	/** The channel estimator entity. */
	ChannelEstimator channelEstimator;
	/** The compressor's belief on the system's state, in the order of NC_B, NC_G, SC_B, SC_G, FC_0, FC_1, ..., FC_{W - 1}*/
	DoubleMatrix belief; // The states are defined 
	/** System's state transition matrix, corresponding to action IR, FO and SO, respectively. */
	DoubleMatrix [] funcTransition;
	/** System's observation probability vector, corresponding to observation bad and good, respectively. */
	DoubleMatrix [] funcObservation;
	
	/** The vectors representing compressor's policy */
	DoubleMatrix [] vectorPolicies;
	/** The action to take corresponding to each vector */
	int [] actionPolicies;
	/** The reward corresponding to each vectors at current belief, used to select the best action */
	double [] reward;
	
	/**
	 * Create a POMDP compressor
	 * @param W
	 * @param pFA
	 * @param pMD
	 * @param pBG
	 * @param pGB
	 * @param filename the .policy file to parse the policy vectors and actions
	 * @see #parsePolicy(String)
	 */
	public CompressorPOMDP(int W, double pFA, double pMD, double pBG, double pGB, String filename){
		this.W = W;
		this.pFA = pFA;
		this.pMD = pMD;
		this.pBG = pBG;
		this.pGB = pGB;
		double pBB = 1 - pBG;
		double pGG = 1 - pGB;
		
		belief = DoubleMatrix.zeros(1, 4 + W); 
		reset();
		
		// initialize #vectorPolicies and #actionPolicies
		parsePolicy(filename);
		
		reward = new double [vectorPolicies.length];
		
		// initialize transition function
		funcTransition = new DoubleMatrix[3];
		for (int a = 0; a < 3; a++) funcTransition[a] = DoubleMatrix.zeros(4 + W, 4 + W);
		for (int a = 0; a < 3; a++) { // Transition probability common to all actions
			funcTransition[a].put(0, 0, pBB);
			funcTransition[a].put(1, 0, pGB);
			funcTransition[a].put(4, 4, pGG);
			funcTransition[a].put(4, 5, pGB);
			funcTransition[a].put(2, 2, pBB);
			funcTransition[a].put(3, 2, pGB);
			
			for (int w = 1; w < W; w++) funcTransition[a].put(w + 4, 4, pBG);
			for (int w = 1; w < W - 1; w++) funcTransition[a].put(w + 4, w + 5, pBB);
			funcTransition[a].put(W + 3, 2, pBB);
		}
		
		// Action-dependent transition probability
		funcTransition[0].put(0, 4, pBG);
		funcTransition[0].put(1, 4, pGG);
		funcTransition[0].put(2, 4, pBG);
		funcTransition[0].put(3, 4, pGG);
		
		funcTransition[1].put(0, 1, pBG);
		funcTransition[1].put(1, 1, pGG);
		funcTransition[1].put(2, 4, pBG);
		funcTransition[1].put(3, 4, pGG);
		
		funcTransition[2].put(0, 1, pBG);
		funcTransition[2].put(1, 1, pGG);
		funcTransition[2].put(2, 3, pBG);
		funcTransition[2].put(3, 3, pGG);
		
		// initialize observation function
		funcObservation = new DoubleMatrix [2]; // 0 represent obs false (bad), 1 represent obs true (good)
		funcObservation[0] = new DoubleMatrix(1, 4 + W); funcObservation[1] = new DoubleMatrix(1, 4 + W);
		funcObservation[0].put(0, 1 - pMD); funcObservation[1].put(0, pMD);
		funcObservation[0].put(2, 1 - pMD); funcObservation[1].put(2, pMD);
		for (int w = 1; w < W; w++) {
			funcObservation[0].put(4 + w, 1 - pMD); funcObservation[1].put(4 + w, pMD); 
		}
		
		funcObservation[0].put(1, pFA); funcObservation[1].put(1, 1 - pFA);
		funcObservation[0].put(3, pFA); funcObservation[1].put(3, 1 - pFA);
		funcObservation[0].put(4, pFA); funcObservation[1].put(4, 1 - pFA);
	}
	
	/**
	 * Initialize {@link #vectorPolicies} and {@link #actionPolicies} by parsing the policy file
	 * @param filename the filename of the .policy file
	 */
	void parsePolicy(String filename) {
		try {
			File filePolicy = new File(filename);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(filePolicy);
			
			doc.getDocumentElement().normalize();
			NodeList vectors = doc.getElementsByTagName("Vector");
			
			vectorPolicies = new DoubleMatrix [vectors.getLength()];
			actionPolicies = new int [vectors.getLength()];
			for (int i = 0; i < vectors.getLength(); i++) {
				Node vector = vectors.item(i);
				String [] vectorStr = vector.getTextContent().trim().split(" ");
				vectorPolicies[i] = new DoubleMatrix(vectorStr.length, 1);
				for (int j = 0; j < vectorStr.length; j++) {
					vectorPolicies[i].put(j, Double.parseDouble(vectorStr[j]));
				}
				
				actionPolicies[i] = Integer.parseInt(((Element)vector).getAttribute("action"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Update the compressor's belief on the system state
	 * @param typePacket, the packet it transmits (action it takes), takes value from 0, 1, 2
	 * @param obsChannel, the observed channel state from the channel estimator
	 * FIXME: implement this function according to (12.2) and (12.3)
	 */
	public void updateBelief(int typePacket, boolean obsChannel) {
		
	}
	
	/**
	 * Reset the compressor's belief on the system state: the channel should be on the steady state of the G-E model and the decompressor should be in NC state
	 * @see Decompressor#reset()
	 */
	public void reset() {
		for (int i = 0; i < 4 + W; i++) belief.put(i, 0);
		belief.put(0, pGB / (pBG + pGB));
		belief.put(1, pBG / (pBG + pGB));		
	}
	
	/**
	 * Transmit the packet that maximize the expected reward.
	 */
	public int transmit() {
		for (int p = 0; p < reward.length; p++) {
			reward[p] = belief.dot(vectorPolicies[p]);
		}
		
		double maxReward = reward[0];
		int policyMaxReward = 0;
		for (int p = 1; p < reward.length; p++) {
			if (maxReward < reward[p]) {
				maxReward = reward[p];
				policyMaxReward = p;
			}
		}
		
		return actionPolicies[policyMaxReward];
	}
}
