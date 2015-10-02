import java.util.*;
import GivenTools.*;

public class Handshake {
	public static byte[] encode(byte[] info_hash, byte[] peer_id) {
		byte[] bittorentprotocol = "BitTorrent protocol".getBytes();
		// ByteBuffer handshake = ByteBuffer.allocate(1 + bittorentprotocol.length + 8 + 20 + 20);
		byte[] handshake = new byte[1 + bittorentprotocol.length + 8 + 20 + 20];
		handshake[0] = (byte)19;
		System.arraycopy(bittorentprotocol, 0, handshake, 1, bittorentprotocol.length);
		System.arraycopy(info_hash, 0, handshake, 28, 20);
		System.arraycopy(peer_id, 0, handshake, 48, 20);
		// byte[] reserved = new byte[8];
		// Arrays.fill(handshake, (byte)0);
		
		for (int i = 0; i < handshake.length; i++) {
			// System.out.println(i);
			System.out.println(handshake[i]);
		}
		
		return handshake;
	}

	// public static byte[] decode(byte[] handshake) {

	// }
}