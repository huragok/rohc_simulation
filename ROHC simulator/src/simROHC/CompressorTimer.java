package simROHC;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * The simplified U-mode ROHC compressor model defined by 3 timers.
 *
 */
public class CompressorTimer implements Compressor {
	
	final int timeOutIR2SO;
	final int timeOutSO2FO;
	final int timeOutFO2SO;
	
	int typeCurrent;
	int timer;

	List<Integer> log;
	
	CompressorTimer (int timeOutIR2SO, int timeOutSO2FO, int timeOutFO2SO) {
		this.timeOutIR2SO = timeOutIR2SO;
		this.timeOutSO2FO = timeOutSO2FO;
		this.timeOutFO2SO = timeOutFO2SO;
		
		typeCurrent = 0;
		timer = 0;
		
		log = new ArrayList<Integer> ();
	}

	public void reset() {
		typeCurrent = 0;
		timer = 0;
		log.clear();
	}
	
	public int transmit() {
		int type = typeCurrent;
		timer++;
		switch (typeCurrent) {
			case 0: {
				if (timer >= timeOutIR2SO) {
					typeCurrent = 2;
					timer = 0;
				}
				break;
			}
			case 1: {
				if (timer >= timeOutSO2FO) {
					typeCurrent = 1;
					timer = 0;
				}
				break;
			}
			default: {
				if (timer >= timeOutFO2SO) {
					typeCurrent = 2;
					timer = 0;
				}
			}
		}
		
		log.add(type);
		return type;
	}
}
