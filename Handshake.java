import java.nio.*;
import java.util.*;
import GivenTools.*;

public class Handshake {
	public final ByteBuffer info_hash;
	public final String peer_id;
	public final String protocol_str;
	public final byte[] array;

	/**
	 * Creates a handshake out of a TorrentInfo object and a
	 * peer id string. Mostly for cleaner code.
	 * @param  info    a TorrentInfo object for the wanted torrent
	 * @param  peer_id a unique id for the peer, should only be 20 bytes
	 */
	public Handshake(TorrentInfo info, String peer_id) {
		this(info.info_hash.array(), peer_id);
	}

	/**
	 * Creates a Handshake object with a corresponding info hash,
	 * peer id, and default protocol string.
	 * @param  info_hash    SHA1 hash of the info key in metainfo file
	 * @param  peer_id      20 byte array from string used as
	 *                      a unique id for peer
	 */
	public Handshake(byte[] info_hash, String peer_id) {
		this(info_hash, peer_id, "BitTorrent protocol");
	}

	/**
	 * More generic constructor that will create a Handshake object
	 * with any protocol string
	 * @param  info_hash    SHA1 hash of the info key in metainfo file
	 * @param  peer_id      20 byte array from string used as
	 *                      a unique id for peer
	 * @param  protocol_str String identifier of the protocol
	 */
	public Handshake(byte[] info_hash, String peer_id, String protocol_str) {
		byte[] protocol_str_bytes = protocol_str.getBytes();

		this.array = encode(info_hash, peer_id.getBytes(), protocol_str_bytes);
		this.protocol_str = new String(Arrays.copyOfRange(this.array, 1, 1 + protocol_str_bytes.length));
		this.info_hash = ByteBuffer.wrap(
									Arrays.copyOfRange(
											this.array,
											1 + protocol_str_bytes.length + 8,
											1 + protocol_str_bytes.length + 8 + 20));
		this.peer_id = new String(Arrays.copyOfRange(
											this.array,
											1 + protocol_str_bytes.length + 8 + 20,
											1 + protocol_str_bytes.length + 8 + 20 + 20));
	}

	/**
	 * Constructor that takes in all fields that have already been
	 * properly initialized. Primarily for not wasting space by copying
	 * data when decoding a handshake.
	 * @param  info_hash    SHA1 hash of the info key in metainfo file
	 * @param  peer_id      20 byte string from string used as
	 *                      a unique id for peer
	 * @param  protocol_str String identifier of the protocol
	 * @param  array        byte[] used by all other passed ByteBuffers
	 *                      In the case for decoding, this is what came
	 *                      back as the handshake.
	 */
	private Handshake(ByteBuffer info_hash, String peer_id, String protocol_str, byte[] array) {
		this.array = array;
		this.protocol_str = protocol_str;
		this.info_hash = info_hash;
		this.peer_id = peer_id;
	}

	public static byte[] encode(byte[] info_hash, byte[] peer_id, byte[] protocol_str) {
		byte[] handshake = new byte[1 + protocol_str.length + 8 + 20 + 20];
		handshake[0] = (byte)protocol_str.length;
		System.arraycopy(protocol_str, 0, handshake, 1, protocol_str.length);
		System.arraycopy(info_hash, 0, handshake, (1 + protocol_str.length + 8), 20);
		System.arraycopy(peer_id, 0, handshake, (1 + protocol_str.length + 8 + 20), 20);
		
		// for (int i = 0; i < handshake.length; i++) {
		// 	// System.out.println(i);
		// 	System.out.println(handshake[i]);
		// }

		return handshake;
	}

	public static Handshake decode(byte[] handshake) {
		int protocol_length = (int)handshake[0];
		String protocol_str = new String(Arrays.copyOfRange(handshake, 1, 1 + protocol_length));
		ByteBuffer info_hash = ByteBuffer.wrap(
											Arrays.copyOfRange(
													handshake,
													1 + protocol_length + 8,
													1 + protocol_length + 8 + 20));
		String peer_id = new String(Arrays.copyOfRange(
												handshake,
												1 + protocol_length + 8 + 20,
												1 + protocol_length + 8 + 20 + 20));
		return new Handshake(info_hash, peer_id, protocol_str, handshake);
	}
}