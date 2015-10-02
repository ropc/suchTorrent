import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import GivenTools.*;

public class Peer {
	public final String ip;
	public final String peer_id;
	public final int port;
	public Socket sock;

	public static Peer peerFromMap(Map<ByteBuffer, Object> peerMap) {
		String ip = new String(((ByteBuffer)peerMap.get(TorrentHandler.KEY_IP)).array());
		String peer_id = new String(((ByteBuffer)peerMap.get(TorrentHandler.KEY_PEER_ID)).array());
		int port = (int)peerMap.get(TorrentHandler.KEY_PORT);
		return new Peer(ip, peer_id, port);
	}

	public Peer(String ip, String peer_id, int port) {
		this.ip = ip;
		this.peer_id = peer_id;
		this.port = port;
	}

	private void createSocket() {
		try {
			sock = new Socket(ip, port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handshake(TorrentInfo info) {
		Handshake myHandshake = new Handshake(info.info_hash.array(), peer_id.getBytes());
		// byte[] handshake = Handshake.encode(info.info_hash.array(), peer_id.getBytes());
		System.out.println("protocol is: " + myHandshake.protocol_str.toString());
		if (sock == null) {
			createSocket();
		}
		if (sock != null) {
			byte[] peer_bytes = new byte[myHandshake.array.length];
			try {
				DataOutputStream output = new DataOutputStream(sock.getOutputStream());
				DataInputStream input = new DataInputStream(sock.getInputStream());
				output.write(myHandshake.array, 0, myHandshake.array.length);
				input.read(peer_bytes);


				// System.out.println(ph.);
				// for (int i = 0; i < peerHandshake.length; i++) {
				// 	// System.out.println(i);
				// 	System.out.println(peerHandshake[i]);
				// }
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}