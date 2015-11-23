package simROHC;

/**
 * 
 * The ROHC compressor interface.
 * @author wenhaowu
 *
 */
public interface Compressor {
	/**
	 * Reset the compressor to its initial state, when a new stream arrives (static header field changes)
	 */
	public void reset();
	
	/**
	 * The compressor transmit a packet according to its current state, return a value from 0, 1, 2 (IR, FO, SO)
	 * TODO: and update its own state
	 */
	public int transmit();
}
