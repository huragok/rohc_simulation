package simROHC;

/**
* A <code>Simulator</code> simulates a simple ROHC model in LTE protocol stack and Gilbert-Elliot channel model.
*
* @author Wenhao Wu wnhwu@ucdavis.edu
*/
public class Simulator {
	
	public static void main (String [] args) {
		int W = 13;
		int lB = 10;
		double eps = 0.2;
		double pFA = 0.1;
		double pMD = 0.1;
		String filename = "out.policy";
		int N = 100; // Number of packets to transmit
		
		// Create the components of the simulator
		Decompressor decompressor = new Decompressor(W);
		Channel channel = new Channel(eps, lB);
		CompressorPOMDP.ChannelEstimator channelEstimator = new CompressorPOMDP.ChannelEstimator(channel, pFA, pMD);
		Compressor compressor = new CompressorPOMDP(W, channel.pBG, channel.pGB, channelEstimator, filename);
		
		// Start the simulation
		for (int n = 0; n < N; n++) {
			int typePacket = compressor.transmit(); // Compressor takes an action by transmitting a packet and updates its own state
			decompressor.next(channel.isGood, typePacket); // Decompressor update its state according to the actual channel state and the packet (if received)
			channel.next(); // Update the channel state
		}
		
		System.out.println("Simulation completed");
	}
}