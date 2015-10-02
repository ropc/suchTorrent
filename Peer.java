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

	public byte[] getHandshake(TorrentInfo info) {
		byte[] bittorentprotocol = "BitTorrent protocol".getBytes();
		// ByteBuffer handshake = ByteBuffer.allocate(1 + bittorentprotocol.length + 8 + 20 + 20);
		byte[] handshake = new byte[1 + bittorentprotocol.length + 8 + 20 + 20];
		handshake[0] = (byte)19;
		System.arraycopy(bittorentprotocol, 0, handshake, 1, bittorentprotocol.length);
		System.arraycopy(info.info_hash.array(), 0, handshake, 28, 20);
		System.arraycopy(peer_id.getBytes(), 0, handshake, 48, 20);
		// byte[] reserved = new byte[8];
		// Arrays.fill(handshake, (byte)0);
		
		for (int i = 0; i < handshake.length; i++) {
			// System.out.println(i);
			System.out.println(handshake[i]);
		}

		return handshake;
	}

	private void createSocket() {
		try {
			sock = new Socket(ip, port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handshake(TorrentInfo info) {
		byte[] handshake = getHandshake(info);
		if (sock == null) {
			createSocket();
		}
		if (sock != null) {
			byte[] peerHandshake = new byte[handshake.length];
			try {
				DataOutputStream output = new DataOutputStream(sock.getOutputStream());
				DataInputStream input = new DataInputStream(sock.getInputStream());
				output.write(handshake, 0, handshake.length);
				input.read(peerHandshake);
				for (int i = 0; i < peerHandshake.length; i++) {
					// System.out.println(i);
					System.out.println(peerHandshake[i]);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}