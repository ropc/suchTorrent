import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import GivenTools.*;

public class Peer {
	public final String ip;
	public final String peer_id;
	public final int port;
	public PeerDelegate delegate;
	public Socket sock;
	private DataInputStream input;
	private DataOutputStream output;
	public Boolean isChocking;
	public Boolean isInterested;
	public Boolean amChocking;
	public Boolean amInterested;

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
		isChocking = true;
		isInterested = false;
		amChocking = true;
		amInterested = false;
	}

	public void connect() {
		try {
			sock = new Socket(ip, port);
			output = new DataOutputStream(sock.getOutputStream());
			input = new DataInputStream(sock.getInputStream());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (input != null) {
			try {
				input.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (output != null) {
			try {
				output.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (sock != null && !sock.isClosed()) {
			try {
				sock.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void handshake(TorrentInfo info, String local_peer_id) {
		Handshake localHandshake = new Handshake(info, local_peer_id);
		if (sock == null) {
			connect();
		}
		if (sock != null) {
			byte[] peer_bytes = new byte[localHandshake.array.length];
			try {
				// DataOutputStream output = new DataOutputStream(sock.getOutputStream());
				// DataInputStream input = new DataInputStream(sock.getInputStream());
				output.write(localHandshake.array);
				output.flush();

				////
				System.out.println("my handshake:");
				for (byte muhByte : localHandshake.array) {
					System.out.print(muhByte);
				}
				System.out.print("\n");
				////


				input.read(peer_bytes);
				Handshake peerHandshake = Handshake.decode(peer_bytes);

				////
				System.out.println("peer handshake:");
				for (byte muhByte : peerHandshake.array) {
					System.out.print(muhByte);
				}
				System.out.print("\n");
				////
				Boolean peerIsLegit;
				if (localHandshake.info_hash.compareTo(peerHandshake.info_hash) == 0 && peer_id.equals(peerHandshake.peer_id)) {
					System.out.println("peer is legit");
					peerIsLegit = true;
					//////
					output.write(Message.INTERESTED);
					output.flush();

					System.out.println("my message:");
					for (byte muhByte : Message.INTERESTED) {
						System.out.print(muhByte);
					}
					System.out.print("\n");

					byte[] requestMessage = Message.request(0, 0, 16384);
					output.write(requestMessage);
					output.flush();
					System.out.println("message request: ");
					for (byte muhByte : requestMessage) {
						System.out.print(muhByte);
						System.out.print(" ");
					}
					System.out.print("\n");


					System.out.println("peer response:");
					byte[] peerMessageBuffer = new byte[5];
					while (input.read(peerMessageBuffer) != -1) {
						for (byte muhByte : peerMessageBuffer) {
							System.out.print(muhByte);
							System.out.print(" ");
						}
						System.out.print("\n");
					}
					/////
				} else {
					System.out.println("peer is a fake");
					peerIsLegit = false;
				}
				delegate.peerDidHandshake(this, peerIsLegit);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}