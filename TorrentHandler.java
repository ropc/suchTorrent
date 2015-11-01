/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import GivenTools.*;

/**
 * TorrentHandler will take care of managing the peers,
 * schedule communication to the tracker, and maintain the
 * torrent data for a given torrent.
 */
public class TorrentHandler implements TorrentDelegate, PeerDelegate, Runnable {
	protected final TorrentInfo info;
	public Tracker tracker;
	public final String escaped_info_hash;
	protected String local_peer_id;
	public int uploaded;
	public int downloaded;
	public int size;
   public int listenPort;
	public Writer fileWriter;
	public MessageData[] all_pieces;

	protected BlockingQueue<PeerEvent<? extends EventPayload>> eventQueue;
	protected List<Peer> connectedPeers;
	protected Bitfield localBitfield;


   public ByteBuffer getHash(){
      return info.info_hash;
   }
   public void shutdown(){
      
   }
   public void status(){

   }


	/**
	 * create attempts to create a TorrentHandler out of a
	 * given torrent file name and save file name
	 * @param  torrentFilename name of the torrent file to be used
	 * @param  saveFileName    name of the data file the torrent data will be written to
	 * @return                 a properly initialized TorrentHandle if no errors,
	 *                           if there are errors, it returns null
	 */
	public static TorrentHandler create(String torrentFilename, String saveFileName, int port) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(torrentFilename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, RUBTClient.generatePeerId(), port, saveFileName);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("could not create torrent handler");
		}
		return newTorrent;
	}

	/**
	 * TorrentHandler constructor, will throw if cannot initialize
	 * all required fields
	 * @param  info              TorrentInfo instance that describes the torrent to be downloaded
	 * @param  escaped_info_hash used primarily for communications to the tracker
	 * @param  peer_id           the peer_id to be used by this client
	 * @param  saveFileName      name of the filename to be saved to
	 * @throws IOException       will throw if cannot correctly initialize fileWriter
	 *         						(which writes to the given filename)
	 */
	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String peer_id, int port, String saveFileName) throws IOException {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		local_peer_id = peer_id;
      listenPort = port;
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
		tracker = new Tracker(escaped_info_hash, peer_id, listenPort, info.announce_url.toString(), size);
		all_pieces = new MessageData[info.piece_hashes.length];
		fileWriter = new Writer(saveFileName, info.piece_length);
		eventQueue = new LinkedBlockingQueue<>();
		connectedPeers = new ArrayList<>();
		localBitfield = new Bitfield(info.piece_hashes.length);
	}

	/**
	 * Verifies the hash of a given piece inside a message
	 * against the hash stored in the torrent file
	 * @param  pieceMessage message containing the piece to be verified
	 * @return              true if hashes match, false otherwise
	 */
	protected Boolean pieceIsCorrect(MessageData pieceMessage) {
		Boolean isCorrect = false;
		if (pieceMessage.type == Message.PIECE) {
			try {
				MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
				sha1.update(pieceMessage.message, 13, pieceMessage.message.length - 13);
				byte[] generatedPieceHash = sha1.digest();
				byte[] torrentFilePieceHash = info.piece_hashes[pieceMessage.pieceIndex].array();
				if (Arrays.equals(generatedPieceHash, torrentFilePieceHash)) {
					isCorrect = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return isCorrect;
	}

	/**
	 * Called once download is completed, will only try to save
	 * if the the download is complete.
	 */
	public void saveTofile() {
		if (downloaded == info.file_length) {
			System.out.println("Downloaded everything. Writing to file.");
			for (MessageData pieceData : all_pieces) {
				fileWriter.writeMessage(pieceData.message);
			}
		} else
			System.err.println("didnt download everything?");
	}

	/**
	 * get the piece size based on index.
	 * only piece that is differently sized is the last piece
	 * @param  pieceIndex index of this piece
	 * @return            pieceSize, 0 if no such piece
	 *                        (if index is beyond bounds of piece count)
	 */
	public int getPieceSize(int pieceIndex) {
		int pieceSize;
		if (pieceIndex == info.piece_hashes.length - 1)
			pieceSize = size % info.piece_length;
		else if (pieceIndex < info.piece_hashes.length)
			pieceSize = info.piece_length;
		else
			pieceSize = 0;
		return pieceSize;
	}

	/**
	 * this is called by the peer when a message is recieved
	 * the return value indicates whether or not the peer should
	 * continue trying to read from its socket
	 * @param  peer    peer that sent this message
	 * @param  message the MessageData object containing information about the message
	 * @return         true if peer should continue reading, false if not
	 */
	public void peerDidReceiveMessage(Peer peer, MessageData message) {
		try {
			eventQueue.put(new PeerEvent<MessageData>(PeerEvent.Type.MESSAGE_RECEIVED, peer, message));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void processMessageEvent(Peer peer, MessageData message) {
		// Peer peer = messageEvent.sender;
		// MessageData message = messageEvent.payload;
		try {
			MessageData requestMsg = null;
			String outputString = "";
			if (message.type == Message.BITFIELD) {
				requestMsg = new MessageData(Message.INTERESTED);
				outputString = "sending INTERESTED";
			} else if (message.type == Message.UNCHOKE) {
				requestMsg = new MessageData(Message.REQUEST, 0, 0, info.piece_length);
				outputString = "sending request for piece 0";
				System.out.println("notifying tracker will start to download");
				tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.STARTED);
			} else if (message.type == Message.PIECE) {
				int nextPiece;
				if (pieceIsCorrect(message)) {
					requestMsg = new MessageData(Message.HAVE, message.pieceIndex);
					peer.send(requestMsg);
					System.out.println("sending HAVE piece " + message.pieceIndex + " to peer");
					requestMsg = null;
					all_pieces[message.pieceIndex] = message;
					nextPiece = message.pieceIndex + 1;
					downloaded += getPieceSize(message.pieceIndex);
					// System.out.println("downloaded: " + downloaded + " picece size: " + getPieceSize(message.pieceIndex));
				} else {
					System.err.println("piece " + message.pieceIndex + " was incorrect.");
					nextPiece = message.pieceIndex;
				}
				if (nextPiece < info.piece_hashes.length) {
					int pieceSize = getPieceSize(nextPiece);
					requestMsg = new MessageData(Message.REQUEST, nextPiece, 0, pieceSize);
					outputString = "sending request for piece " + nextPiece;
				} else {
					System.out.println("done downloading. notifying tracker.");
					tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.COMPLETED);
					System.out.println("disconnecting from peer: " + peer.peer_id);
					peer.disconnect();
					saveTofile();
				}
			}
			if (requestMsg != null) {
				peer.send(requestMsg);
				System.out.println(outputString);
				// for (byte muhByte : requestMsg.message)
				// 	System.out.print(muhByte + " ");
				// System.out.println();
			} else {
				System.err.println("TODO: send an event to close the connection");
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("notifying tracker that download will stop");
			tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.STOPPED);
			System.err.println("TODO: send an event to close the connection");
		}
	}

	/**
	 * will be called by peer after a handshake exchange has been
	 * checked. Allows torrnent handler to know what peers have
	 * successfully connected.
	 * @param peer        the peer that has checked the handshakes
	 * @param peerIsLegit true if the handshake was correct,
	 *                    false if handshake was incorrect
	 */
	public void peerDidHandshake(Peer peer, Boolean peerIsLegit) {
		PeerEvent.Type eventType;
		if (peerIsLegit) {
			eventType = PeerEvent.Type.HANDSHAKE_SUCCESSFUL;
		} else {
			eventType = PeerEvent.Type.HANDSHAKE_FAILED;
		}
		try {
			eventQueue.put(new PeerEvent<EventPayload>(eventType, peer));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called by peer if it tried to connect but failed for any reason
	 * @param peer peer that failed to create a connection
	 */
	public void peerDidFailToConnect(Peer peer) {
		try {
			eventQueue.put(new PeerEvent<EventPayload>(PeerEvent.Type.CONNECTION_FAILED, peer));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getLocalPeerId() {
		return local_peer_id;
	}

	public TorrentInfo getTorrentInfo() {
		return info;
	}


	protected void consumeEvents() {
		PeerEvent<? extends EventPayload> event = null;
		try {
			while((event = eventQueue.take()) != null) {
				if (event.type == PeerEvent.Type.CONNECTION_FAILED) {
					System.err.println("Could not connect to peer " + event.sender.peer_id + " at ip: " + event.sender.ip);
					// can ask user what to do here
				} else if (event.type == PeerEvent.Type.MESSAGE_RECEIVED && event.payload instanceof MessageData) {
					processMessageEvent(event.sender, (MessageData)event.payload);
				} else if (event.type == PeerEvent.Type.HANDSHAKE_SUCCESSFUL) {
					connectedPeers.add(event.sender);
					System.out.println("Connected to peer: " + event.sender.ip);
				} else if (event.type == PeerEvent.Type.HANDSHAKE_FAILED) {
					System.err.println("handshake with peer " + event.sender.peer_id + " failed.");
					event.sender.disconnect();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start torrent handler. Which will communicate with the tracker
	 * and parse through the tracker response to create a connection to
	 * the peers that begin with "-RU".
	 */
	@SuppressWarnings("unchecked")
	public void start() {
		Map<ByteBuffer, Object> decodedData = tracker.getTrackerResponse(uploaded, downloaded);
		ToolKit.print(decodedData);
		if (decodedData != null) {
			Object value = decodedData.get(Tracker.KEY_PEERS);
			ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)value;
			// ToolKit.print(peers);
			if (peers != null) {
				for (Map<ByteBuffer, Object> map_peer : peers) {
					ByteBuffer ip = (ByteBuffer)map_peer.get(Tracker.KEY_IP);
					if (ip != null) {
						String new_peer_ip = new String(ip.array());
						if (new_peer_ip.compareTo("128.6.171.130") == 0 ||
							new_peer_ip.compareTo("128.6.171.131") == 0)
						{
							// establish a connection with this peer
							Peer client = Peer.peerFromMap(map_peer, this);
							client.startThreads();
						}
					}
				}
			} else {
				System.err.println("Could not find key PEERS in decoded tracker response");
			}
		} else {
			System.err.println("Tracker response came back empty, please try again.");
		}
		consumeEvents();
	}

   public void run(){
      start();
   }
}
