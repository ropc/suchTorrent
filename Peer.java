import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import GivenTools.*;

public class Peer {
	public final String ip;
	public final String peer_id;
	public final int port;
	public TorrentHandler delegate;
	public Socket sock;
	private DataInputStream input;
	private DataOutputStream output;
	public byte[] bitfield;
	protected Boolean isChocking;
	protected Boolean isInterested;
	protected Boolean amChocking;
	protected Boolean amInterested;

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
		bitfield = null;
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

	public Handshake readHandshake() {
		Handshake peerHandshake = null;
		if (input != null) {
			try {
				byte pstrlen = input.readByte();
				int totalLength = (int)pstrlen + 49;
				// System.out.println("going to read handshake of total length: " + totalLength);
				byte[] peer_bytes = new byte[totalLength];
				peer_bytes[0] = pstrlen;
				input.readFully(peer_bytes, 1, totalLength - 1);
				peerHandshake = Handshake.decode(peer_bytes);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return peerHandshake;
	}

	public void send(byte[] message) throws IOException {
		if (output != null) {
			output.write(message);
			output.flush();
		}
	}

	public void startReading() {
		Boolean isReading = true;
		while (isReading) {
			try {
				byte[] messageBuffer = new byte[4];
				input.readFully(messageBuffer);
				int messageLength = ByteBuffer.wrap(messageBuffer).getInt();
				System.out.println();
				System.out.print("reading first 4 bytes: ");
				for (byte i : messageBuffer) {
					System.out.print(i + " ");
				}
				System.out.println("to int:" + messageLength);

				if (messageLength > 0) {
					// System.out.println("reading next " + messageLength + " bytes:");
					byte[] newMsgBuf = new byte[4 + messageLength];
					System.arraycopy(messageBuffer, 0, newMsgBuf, 0, 4);
					input.readFully(newMsgBuf, 4, messageLength);
					messageBuffer = newMsgBuf;
				}

				System.out.println("peer response:");
				for (byte i : messageBuffer) {
					System.out.print(i + " ");
				}
				System.out.println("count: " + messageBuffer.length);
				
				// this should be more like
				// Message msg = Message.decode(messageBuffer)
				// isReading = processMessage(msg)
				MessageData message = new MessageData(messageBuffer);
				System.out.println("message " + message.type.toString() + " came in");
				switch (message.type) {
					case BITFIELD:
						bitfield = message.bitfield;
						break;
					case UNCHOKE:
						setIsChocking(false);
						break;
					case CHOKE:
						setIsChocking(true);
						break;
					case INTERESTED:
						setIsInterested(true);
						break;
					case NOTINTERESTED:
						setIsInterested(false);
						break;
				}

				isReading = delegate.peerDidReceiveMessage(this, message);
			} catch (Exception e) {
				e.printStackTrace();
				isReading = false;
				disconnect();
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
				
				Handshake peerHandshake = readHandshake();

				Boolean peerIsLegit;
				if (localHandshake.info_hash.compareTo(peerHandshake.info_hash) == 0 && peer_id.equals(peerHandshake.peer_id)) {
					peerIsLegit = true;
				} else {
					peerIsLegit = false;
				}
				delegate.peerDidHandshake(this, peerIsLegit);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * getters/setters for choking/interested
	 * some are protected as they should only be changed beacause
	 * of incoming messages, which are processed by this class
	 */

	public Boolean getIsChocking() {
		return isChocking;
	}

	protected void setIsChocking(Boolean value) {
		isChocking = value;
	}

	public Boolean getAmChocking() {
		return amChocking;
	}

	public void setAmChocking(Boolean value) {
		amChocking = value;
	}

	public Boolean getIsInterested() {
		return isInterested;
	}

	protected void setIsInterested(Boolean value) {
		amChocking = value;
	}

	public Boolean getAmInterested() {
		return amInterested;
	}

	public void setAmInterested(Boolean value) {
		amChocking = value;
	}
}