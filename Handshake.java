import java.nio.*;
import java.util.*;
import GivenTools.*;

public class Handshake {
	public final ByteBuffer info_hash;
	public final ByteBuffer peer_id;
	public final CharBuffer protocol_str;
	public final byte[] array;

	/**
	 * Creates a Handshake object with a corresponding info hash,
	 * peer id, and default protocol string.
	 * @param  info_hash indentifier from .torrent
	 * @param  peer_id   indentifier from Peer object
	 */
	public Handshake(byte[] info_hash, byte[] peer_id) {
		this(info_hash, peer_id, "BitTorrent protocol");
	}

	/**
	 * More generic constructor that will create a Handshake object
	 * with any protocol string
	 * @param  info_hash    identifier from .torrent
	 * @param  peer_id      identifier from Peer object
	 * @param  protocol_str String identifier of the protocol
	 * @return              [description]
	 */
	public Handshake(byte[] info_hash, byte[] peer_id, String protocol_str) {
		byte[] protocol_str_bytes = protocol_str.getBytes();
		this.array = encode(info_hash, peer_id, protocol_str_bytes);
		this.protocol_str = ByteBuffer.wrap(this.array, 1, protocol_str_bytes.length).asReadOnlyBuffer().asCharBuffer();
		this.info_hash = ByteBuffer.wrap(this.array, 1 + protocol_str_bytes.length + 8, 20).asReadOnlyBuffer();
		this.peer_id = ByteBuffer.wrap(this.array, (1 + protocol_str_bytes.length + 8 + 20), 20).asReadOnlyBuffer();
	}

	// Internally used by decode()
	private Handshake(ByteBuffer info_hash, ByteBuffer peer_id, CharBuffer protocol_str, byte[] array) {
		this.array = array;
		this.protocol_str = protocol_str.asReadOnlyBuffer();
		this.info_hash = info_hash.asReadOnlyBuffer();
		this.peer_id = peer_id.asReadOnlyBuffer();
	}

	public static byte[] encode(byte[] info_hash, byte[] peer_id) {
		return encode(info_hash, peer_id, "BitTorrent protocol".getBytes());
	}

	public static byte[] encode(byte[] info_hash, byte[] peer_id, byte[] protocol_str) {
		byte[] handshake = new byte[1 + protocol_str.length + 8 + 20 + 20];
		handshake[0] = (byte)19;
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
		CharBuffer protocol_str = ByteBuffer.wrap(handshake, 1, protocol_length).asCharBuffer();
		ByteBuffer info_hash = ByteBuffer.wrap(handshake, (1 + protocol_length + 8), 20);
		ByteBuffer peer_id = ByteBuffer.wrap(handshake, (1 + protocol_length + 8 + 20), 20);
		return new Handshake(info_hash, peer_id, protocol_str, handshake);
	}
}