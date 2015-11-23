package simROHC;

/**
 * 
 * The Gilbert-Elliot ROHC channel model
 *
 */
public class Channel {
	/** The probability that channel transit from good to bad. */
	final double pGB;
	/** The probability that channel transit from bad to good. */
	final double pBG;
	/** The current channel state, is good or not*/
	boolean isGood;
	
	/**
	 * Create a new channel with pBG and pGB, initialize the channel state using the steady state of the G-E model.
	 * @param pBG
	 * @param pGB
	 */
	public Channel(double pBG, double pGB) {
		this.pBG = pBG;
		this.pGB = pGB;
		
		double tmp = Math.random();
		isGood = (tmp < pGB / (pGB + pBG) ? false : true); 
	}
	
	/**
	 * Create a new channel using the alternative definition of G-E model
	 * @param eps the average erasure probability
	 * @param lB the average duration of a sequence of bad states
	 * @see Channel#Channel(double, double)
	 */
	public Channel(double eps, int lB) {
		this(1 / ((double) lB), 1 / ((double) lB) / (1 / eps - 1));
	}
	
	/**
	 * Get the current channel state
	 * @return the current channel state.
	 */
	public boolean getChannelState() {
		return isGood;
	}
	
	/**
	 * Update the channel state.
	 */
	public void next() {
		double tmp = Math.random();
		if (isGood) {
			isGood = tmp > pGB;
		} else {
			isGood = tmp < pBG;
		}
	}
}