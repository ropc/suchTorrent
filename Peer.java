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

	public void handshake(TorrentInfo info, String local_peer_id) {
		Handshake localHandshake = new Handshake(info, local_peer_id);
		if (sock == null) {
			createSocket();
		}
		if (sock != null) {
			byte[] peer_bytes = new byte[localHandshake.array.length];
			try {
				DataOutputStream output = new DataOutputStream(sock.getOutputStream());
				DataInputStream input = new DataInputStream(sock.getInputStream());
				output.write(localHandshake.array, 0, localHandshake.array.length);
				input.read(peer_bytes);
				Handshake peerHandshake = Handshake.decode(peer_bytes);
				if (localHandshake.info_hash.compareTo(peerHandshake.info_hash) == 0 &&
					peer_id.equals(peerHandshake.peer_id)) {
					System.out.println("peer is legit");
				} else {
					System.out.println("peer is a fake");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}