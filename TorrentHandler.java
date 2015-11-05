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
	private long startTime;
	public boolean finished;

	protected BlockingDeque<PeerEvent<? extends EventPayload>> eventQueue;
	protected List<Peer> connectedPeers;
	protected List<Peer> attemptingToConnectPeers;
	protected Bitfield localBitfield;
	protected Queue<Integer> piecesToDownload;
	protected Queue<Integer> requestedPieces;

	public ByteBuffer getHash() {
		return info.info_hash;
	}
	public void shutdown() {
		try {
			eventQueue.putFirst(new PeerEvent<EventPayload>(PeerEvent.Type.SHUTDOWN));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	public void status(){
		System.out.format("downloaded: %d, uploaded: %d\n", downloaded, uploaded);
	}


	/**
	 * create attempts to create a TorrentHandler out of a
	 * given torrent file name and save file name
	 * @param  torrentFilename name of the torrent file to be used
	 * @param  saveFileName    name of the data file the torrent data will be written to
	 * @return                 a properly initialized TorrentHandle if no errors,
	 *                           if there are errors, it returns null
	 */
	public static TorrentHandler create(String torrentFilename, String saveFileName) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(torrentFilename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, saveFileName);
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
	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String saveFileName) throws IOException {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		local_peer_id = RUBTClient.peerId;
      listenPort = RUBTClient.getListenPort();
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
		tracker = new Tracker(escaped_info_hash, info.announce_url.toString(), size);
		all_pieces = new MessageData[info.piece_hashes.length];
		fileWriter = new Writer(saveFileName, info.piece_length);
		eventQueue = new LinkedBlockingDeque<>();
		connectedPeers = new ArrayList<>();
		attemptingToConnectPeers = new ArrayList<>();
		localBitfield = new Bitfield(info.piece_hashes.length);
		piecesToDownload = new ArrayDeque<>(info.piece_hashes.length);
		for (int i = 0; i < info.piece_hashes.length; i++)
			piecesToDownload.add(i);
		requestedPieces = new ArrayDeque<>(info.piece_hashes.length);
		startTime = System.currentTimeMillis();
		finished = false;
	}
   
   public void createIncomingPeer(Handshake peer_hs, Socket sock){
      Peer incPeer = Peer.peerFromHandshake( peer_hs, sock, this);
      
      if (incPeer != null && !incPeer.sock.isClosed() && incPeer.sock.isConnected()){
         connectedPeers.add(incPeer);
         PeerRunnable.HS_StartAndReadRunnable runnable = new PeerRunnable.HS_StartAndReadRunnable(incPeer, peer_hs);
         (new Thread(runnable)).start();     
      }
      else{
         System.err.println("Something fucked up, socket is closed on incPeer");
      }
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
	protected void saveTofile() {
		if (downloaded == info.file_length) {
			int hours,minutes,seconds,extra;
			long time = System.currentTimeMillis();
			time -= startTime;
			time /= 1000;
			hours = (int)time/3600;
			time = time%3600;
			minutes = (int)time/60;
			time= time%60;
			seconds = (int)time;
			System.out.println("Downloaded everything. Writing to file.");
			System.out.println("Time Elapsed since started:"+hours+":"+minutes+":"+seconds);
			for (MessageData pieceData : all_pieces) {
				fileWriter.writeMessage(pieceData.message);
			}
		} else
			System.err.println("didnt download everything?");
	}

	protected void saveTofile(MessageData piece) {
		fileWriter.writeMessage(piece.message);
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
			eventQueue.putLast(new PeerEvent<MessageData>(PeerEvent.Type.MESSAGE_RECEIVED, peer, message));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void processPieceMessage(Peer sender, MessageData message) {
		if (pieceIsCorrect(message)) {
			MessageData requestMsg = new MessageData(Message.HAVE, message.pieceIndex);
			sender.send(requestMsg);
			System.out.println("sending HAVE piece " + message.pieceIndex + " to peer " + sender.ip);
			requestedPieces.remove(message.pieceIndex);
			if (all_pieces[message.pieceIndex] == null) {
				all_pieces[message.pieceIndex] = message;
				saveTofile(message);
				localBitfield.set(message.pieceIndex);
				downloaded = downloaded + getPieceSize(message.pieceIndex);
				// System.out.println("downloaded: " + downloaded + " out of " + info.file_length + " (" + ((double)downloaded / info.file_length) + ")");
				// System.out.println("downloaded piece " + message.pieceIndex + " of size " + getPieceSize(message.pieceIndex));
				System.out.format("downloaded %d out of %d (%.2f %%) (proccessed piece %d of size %d)\n",
					downloaded, info.file_length, 100.0 * (double)downloaded / info.file_length,
					message.pieceIndex, getPieceSize(message.pieceIndex));
			}
			// nextPiece = message.pieceIndex + 1;
			// addDownloaded(getPieceSize(message.pieceIndex));
			// System.out.println("downloaded: " + downloaded + " picece size: " + getPieceSize(message.pieceIndex));
		} else {
			System.out.println("piece " + message.pieceIndex + " was incorrect.");
			piecesToDownload.add(message.pieceIndex);
		}
	}

	protected void processMessageEvent(Peer peer, MessageData message) {
		// Peer peer = messageEvent.sender;
		// MessageData message = messageEvent.payload;
		try {
			System.out.println("Proccessing message " + message.type + " from " + peer.ip);
			if (message.type == Message.BITFIELD) {
				MessageData requestMsg = new MessageData(Message.INTERESTED);
				System.out.println("sending INTERESTED");
				peer.send(requestMsg);
			} else if (message.type == Message.UNCHOKE) {
				// MessageData requestMsg = new MessageData(Message.REQUEST, 0, 0, info.piece_length);
				// System.out.println("sending request for piece 0 to " + peer.ip);
				// peer.send(requestMsg);
				System.out.println("notifying tracker will start to download");
				tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.STARTED);
			} else if (message.type == Message.PIECE) {
				processPieceMessage(peer, message);
			} else if (message.type == Message.REQUEST) {
				if (localBitfield.get(message.pieceIndex) == true) {
					if (all_pieces[message.pieceIndex] != null)
						peer.send(all_pieces[message.pieceIndex]);
					else
						System.err.println("Need to be reading from the file here");
				}
				// else
				// 	Do we want to deal with the case where the peer asks us for things we don't have
			} else if (message.type == Message.INTERESTED) {
				peer.send(new MessageData(Message.UNCHOKE));
			}
			
			
			Integer nextPiece = piecesToDownload.poll();
			if (nextPiece == null) {
				nextPiece = requestedPieces.poll();
				System.out.println("Greddily requesting " + nextPiece);
			}

			if (nextPiece != null) {
				int nextPieceIndex = nextPiece.intValue();
				if (peer.getBitfield().get(nextPieceIndex) == true) {
					int pieceSize = getPieceSize(nextPieceIndex);
					System.out.println("sending request for piece " + nextPieceIndex + " to: " + peer.ip);
					MessageData requestMsg = new MessageData(Message.REQUEST, nextPieceIndex, 0, pieceSize);
					peer.send(requestMsg);
					requestedPieces.add(nextPieceIndex);
				} else {
					piecesToDownload.add(nextPieceIndex);
				}
			}

			if (downloaded == info.file_length) {
				// This might be getting sent early
				int hours,minutes,seconds,extra;
				long time = System.currentTimeMillis();
				System.out.println("done downloading. notifying tracker.");
				tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.COMPLETED);
				finished=true;
				// System.out.println("disconnecting from peer: " + peer.ip);
				time -= startTime;
				time /= 1000;
				hours = (int)time/3600;
				time = time%3600;
				minutes = (int)time/60;
				time= time%60;
				seconds = (int)time;
				System.out.println("Time Elapsed since started:"+hours+":"+minutes+":"+seconds);
				disconnectPeers();
				// should send them an event instead
				// peer.disconnect();
				// saveTofile();
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("notifying tracker that download will stop");
			tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.STOPPED);
			System.err.println("TODO: send an event to close the connection");
		}
	}

	protected void disconnectPeers() {
		for (Peer peer : connectedPeers) {
			System.out.println("shutting down peer " + peer.ip);
			peer.shutdown();
		}
		connectedPeers.clear();
		for (Peer peer : attemptingToConnectPeers) {
			System.out.println("shutting down peer " + peer.ip);
			peer.shutdown();
		}
		attemptingToConnectPeers.clear();
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
			eventQueue.putLast(new PeerEvent<EventPayload>(eventType, peer));
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
			eventQueue.putLast(new PeerEvent<EventPayload>(PeerEvent.Type.CONNECTION_FAILED, peer));
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
		Boolean isRunning = true;
		try {
			while(isRunning && (event = eventQueue.takeFirst()) != null) {
				if (event.type == PeerEvent.Type.CONNECTION_FAILED) {
					System.err.println("Could not connect to peer " + event.sender.peer_id + " at ip: " + event.sender.ip);
					// can ask user what to do here
				} else if (event.type == PeerEvent.Type.MESSAGE_RECEIVED && event.payload instanceof MessageData) {
					processMessageEvent(event.sender, (MessageData)event.payload);
				} else if (event.type == PeerEvent.Type.HANDSHAKE_SUCCESSFUL) {
					attemptingToConnectPeers.remove(event.sender);
					connectedPeers.add(event.sender);
					System.out.println("Connected to peer: " + event.sender.ip);
				} else if (event.type == PeerEvent.Type.HANDSHAKE_FAILED) {
					System.err.println("handshake with peer " + event.sender.peer_id + " failed.");
					event.sender.disconnect();
				} else if (event.type == PeerEvent.Type.SHUTDOWN) {
					disconnectPeers();
					tracker.sendStoppedMessage();
					isRunning = false;
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
	public void run() {
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
							attemptingToConnectPeers.add(client);
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
}
