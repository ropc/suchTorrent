/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import GivenTools.*;

/**
 * A peer that conforms to the BitTorrent protocol
 * This peer will call methods on its PeerDelegate
 * to notify it when certain events have occurred, such as
 * a message has been received.
 */
public class Peer {
	public final String ip;
	public final String peer_id;
	public final int port;
	public PeerDelegate delegate;
	public Socket sock;
	private DataInputStream input;
	private DataOutputStream output;
	public byte[] bitfield;
	protected Boolean isChocking;
	protected Boolean isInterested;
	protected Boolean amChocking;
	protected Boolean amInterested;

	/**
	 * create a Peer from a given HashMap that was decoded
	 * from a Bencoded tracker response
	 * @param  peerMap  HashMap containing peer info
	 * @param  delegate PeerDelegate that will handle events relating to the given peer
	 * @return          initialized Peer object
	 */
	public static Peer peerFromMap(Map<ByteBuffer, Object> peerMap, PeerDelegate delegate) {
		String ip = new String(((ByteBuffer)peerMap.get(Tracker.KEY_IP)).array());
		String peer_id = new String(((ByteBuffer)peerMap.get(Tracker.KEY_PEER_ID)).array());
		int port = (int)peerMap.get(Tracker.KEY_PORT);
		return new Peer(ip, peer_id, port, delegate);
	}

	/**
	 * Peer constructor
	 * @param  ip       ip address of the peer
	 * @param  peer_id  peer id in a String
	 * @param  port     port at which this peer is listening
	 * @param  delegate PeerDelegate that will handle events
	 */
	public Peer(String ip, String peer_id, int port, PeerDelegate delegate) {
		this.ip = ip;
		this.peer_id = peer_id;
		this.port = port;
		this.delegate = delegate;
		isChocking = true;
		isInterested = false;
		amChocking = true;
		amInterested = false;
		bitfield = null;
	}

	/**
	 * Connects to this peer.
	 * Opens a socket and creates input/output streams
	 * If fails, it notifies the PeerDelegate
	 */
	public void connect() {
		try {
			sock = new Socket(ip, port);
			output = new DataOutputStream(sock.getOutputStream());
			input = new DataInputStream(sock.getInputStream());
		} catch (Exception e) {
			e.printStackTrace();
			delegate.peerDidFailToConnect(this);
		}
	}

	/**
	 * Disconnects this peer.
	 * closes the input/output streams and the socket
	 * for this peer
	 */
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

	/**
	 * Reads the input stream and will parse
	 * a handshake.
	 * @return the handshake it finds (handshake the peer sends)
	 */
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

	/**
	 * sends a MessageData if possible. Used primarily by the PeerDelegate
	 * to send a message before listening again
	 * @param  message     message to send
	 * @throws IOException if any errors occur, they will be thrown
	 */
	public void send(MessageData message) throws IOException {
		if (output != null && message != null && message.message != null) {
			output.write(message.message);
			output.flush();
		}
	}

	/**
	 * main loop for Peer
	 * continues to read from the input stream for as long as
	 * delegate.peerDidReceiveMessage() returns true or the socket
	 * is closed
	 */
	public void startReading() {
		Boolean isReading = true;
		while (isReading) {
			try {
				byte[] messageBuffer = new byte[4];
				input.readFully(messageBuffer);
				int messageLength = ByteBuffer.wrap(messageBuffer).getInt();
				System.out.println();
				// System.out.print("reading first 4 bytes: ");
				// for (byte i : messageBuffer) {
				// 	System.out.print(i + " ");
				// }
				// System.out.println("to int:" + messageLength);

				if (messageLength > 0) {
					// System.out.println("reading next " + messageLength + " bytes:");
					byte[] newMsgBuf = new byte[4 + messageLength];
					System.arraycopy(messageBuffer, 0, newMsgBuf, 0, 4);
					input.readFully(newMsgBuf, 4, messageLength);
					messageBuffer = newMsgBuf;
				}

				// System.out.println("peer response:");
				// for (int i = 0; i < 100 && i < messageBuffer.length; i++) {
				// 	System.out.print(messageBuffer[i] + " ");
				// }
				// System.out.println("count: " + messageBuffer.length);

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

	/**
	 * method to start the connection to this peer and begin
	 * communication by sending the handshake
	 * @param info          torrentInfo
	 * @param local_peer_id peer id for the local client (used to create the local handshake)
	 */
	public void start(TorrentInfo info, String local_peer_id) {
		if (sock == null) {
			connect();
		}
		handshake(info, local_peer_id);
	}

	/**
	 * will initiate the handshaking process by sending the local
	 * handshake to the peer. also checks if the returning handshake is
	 * correct. calls delegate to decide what to do next
	 * @param info          info stored in the torrent file
	 * @param local_peer_id local peer id used for creating the local handshake
	 */
	protected void handshake(TorrentInfo info, String local_peer_id) {
		if (sock != null) {
			Handshake localHandshake = new Handshake(info, local_peer_id);
			try {
				output.write(localHandshake.array);
				output.flush();

				////
				// System.out.println("my handshake:");
				// for (byte muhByte : localHandshake.array) {
				// 	System.out.print(muhByte + " ");
				// }
				// System.out.print("\n");
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
	 * These can be modified in the future to allow for more functionality
	 * such as notifying delegate that this peer is no longer chocking
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
