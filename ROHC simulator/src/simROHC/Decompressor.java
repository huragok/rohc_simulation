package simROHC;

import java.util.List;
import java.util.ArrayList;

/**
 * 
 * The ROHC decompressor model
 * 
 */
public class Decompressor {
	/** The 3 possible states of the decompressor, non-context, static-context and full-context. */
	public static enum State {NC, SC, FC};
	/** Capability of the WLSB coding, the maximal number of packets that can be lost in a row without losing the context synchronization */
	final int W;
	/** The current state of the decompressor*/
	State state;
	/** The number of consecutively lost packets, when the decompressor is in FC mode, used to jointly define its state*/
	int w;
	List<LogEntry> log;
	
	/**
	* Creates a new decompressor with the given WLSB capacity. Also initialize it to be in NC state.
	* @see #reset()
	*/
	static class LogEntry {
		int w;
		State state;
		
		public LogEntry(int w, State state) {
			this.w = w;
			this.state = state;
		}
	}
	
	public Decompressor(int W) {
		this.W = W;
		log = new ArrayList<LogEntry> ();
		reset();
	}
	
	/**
	* Reset the decompressor to be in NC state.
	*/
	public void reset() {
		state = State.NC;
		w = 0;
		log.clear();
		log.add(new LogEntry(w, state));
	}
	
	/**
	* Update the decompressor's state given the ROHC channel and the packet transmitted
	* @param channelState whether the ROHC channel is good or not
	* @param typePacket take value from 0, 1, 2 representing IR, FO, SO packets respectively
	*/
	public void next(boolean channelState, int typePacket) {
		if (!channelState) { // Last channel is bad,
			// Remains in NC/SC
			if (state == State.FC) w++;
			if (w >= W) state = State.SC;
		} else { // Last channel is good,
			switch (typePacket) {
				case 1: { // FO 
					if (state == State.SC || state == State.FC) {
						state = State.FC;
						w = 0;
					}
					break;
				}
				case 2: { // SO
					if (state == State.FC) {
						w = 0;
					}
					break;
				}
				default: { // IR
					state = State.FC; w = 0;
				}
			}
		}
		log.add(new LogEntry(w, state));
	}
}
