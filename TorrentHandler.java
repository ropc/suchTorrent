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
	private int uploaded;
	private int downloaded;
	public final int size;
	public int listenPort;
	public Writer fileWriter;
	protected SessionHandler sessionHandler;
	public byte[][] all_pieces;
	private long startTime;
	public boolean finished;

	boolean isRunning;
	boolean didStart = false;
	private BlockingDeque<Callable<Void>> runQueue;
	protected Map<String, Peer> connectedPeers;
	protected Map<String, Peer> attemptingToConnectPeers;
	protected Bitfield localBitfield;
	private Queue<PieceIndexCount> piecesToDownload;
	private Queue<Integer> requestedPieces;

	protected SpeedTestDotNet odometer;
	private Timer chokeTimer;

	public synchronized int getUploaded() {
		int newInt = uploaded;
		return newInt;
	}

	private synchronized void incrementUploaded(int value) {
		uploaded += value;
	}

	public synchronized int getDownloaded() {
		int newInt = downloaded;
		return newInt;
	}

	private synchronized void incrementDownloaded(int value) {
		downloaded += value;
		if (downloaded == size) {
			int hours,minutes,seconds,extra;
			long time = System.currentTimeMillis();
			System.out.println("done downloading. notifying tracker.");
			tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.COMPLETED);
			finished = true;
			// System.out.println("disconnecting from peer: " + peer.ip);
			time -= startTime;
			time /= 1000;
			hours = (int)time/3600;
			time = time%3600;
			minutes = (int)time/60;
			time = time%60;
			seconds = (int)time;
			System.out.println("Time Elapsed since started:"+hours+":"+minutes+":"+seconds);
		}
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
		fileWriter = new Writer(saveFileName, info.piece_length);
		connectedPeers = new HashMap<>();
		attemptingToConnectPeers = new HashMap<>();
		piecesToDownload = new ArrayDeque<>(info.piece_hashes.length);
		requestedPieces = new ArrayDeque<>(info.piece_hashes.length);
		startTime = System.currentTimeMillis();
		finished = false;
		sessionHandler = new SessionHandler(saveFileName, info.piece_length);
		all_pieces = sessionHandler.getPrevSessionData();
		if (all_pieces.length != info.piece_hashes.length)
			all_pieces = new byte[info.piece_hashes.length][info.piece_length];
		localBitfield = Bitfield.decode(sessionHandler.loadSession(), info.piece_hashes.length);
		if (localBitfield == null) {
			System.out.println("bitfield that was read in is null");
			localBitfield = new Bitfield(info.piece_hashes.length);
		}
		System.out.println("local bitfield: " + localBitfield);

		for (int i = 0; i < info.piece_hashes.length; i++) {
			if (localBitfield.get(i) == true)
				downloaded += getPieceSize(i);
			else
				piecesToDownload.add(new PieceIndexCount(i, Integer.MAX_VALUE));
		}

		System.out.println("Pieces to download: " + piecesToDownload);

		if (piecesToDownload.peek() == null)
			finished = true;

		isRunning = true;
		runQueue = new LinkedBlockingDeque<>();
		odometer = new SpeedTestDotNet();
		chokeTimer = new Timer();
	}

   /**
	 * Verifies the hash of a given piece inside a message
	 * against the hash stored in the torrent file
	 * @param  pieceMessage message containing the piece to be verified
	 * @return              true if hashes match, false otherwise
	 */
	
	protected boolean pieceIsCorrect(MessageData pieceMessage) {
		if (pieceMessage.type == Message.PIECE) {
			return pieceIsCorrect(pieceMessage.pieceIndex, pieceMessage.block);
		} else {
			return false;
		}
	}

	protected boolean pieceIsCorrect(int pieceIndex, byte[] block) {
		boolean isCorrect = false;
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(block, 0, block.length);
			byte[] generatedPieceHash = sha1.digest();
			byte[] torrentFilePieceHash = info.piece_hashes[pieceIndex].array();
			if (Arrays.equals(generatedPieceHash, torrentFilePieceHash)) {
				isCorrect = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isCorrect;
	}

	/**
	 * Called once download is completed, will only try to save
	 * if the the download is complete.
	 */
	protected void saveTofile() {
		if (getDownloaded() == info.file_length) {
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
			for (int i = 0; i < info.piece_hashes.length; i++) {
				if (all_pieces[i] != null)
					fileWriter.writeData(i, ByteBuffer.wrap(all_pieces[i]));
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

	protected void disconnectPeers() {
		for (Peer peer : connectedPeers.values()) {
			System.out.println("shutting down peer " + peer.ip);
			peer.shutdown();
		}
		connectedPeers.clear();
		for (Peer peer : attemptingToConnectPeers.values()) {
			System.out.println("shutting down peer " + peer.ip);
			peer.shutdown();
		}
		attemptingToConnectPeers.clear();
	}

	public synchronized void requestNextPiece(final Peer peer) {
		Set<Integer> usefulPiecesFromPeer = localBitfield.not().and(peer.getBitfield()).getSetBitIndexes();
		if (usefulPiecesFromPeer.size() > 0) {
			boolean pieceWasRequested = false;
			List<PieceIndexCount> failedPieces = new ArrayList<>();
			// Will never stay here forever
			for (int i = 0; !pieceWasRequested && i < 10; i++) {
				Integer pieceToRequest = null;
				PieceIndexCount pieceObj = piecesToDownload.poll();
				
				if (pieceObj == null)
					pieceToRequest = requestedPieces.poll();
				else
					pieceToRequest = pieceObj.index;

				if (pieceObj != null && pieceToRequest != null) {
					int pieceIndex = pieceToRequest.intValue();
					int pieceSize = getPieceSize(pieceIndex);
					if (peer.getBitfield().get(pieceIndex) == true) {
						System.out.println("sending REQUEST " + pieceIndex + " to " + peer.ip);
						peer.send(new MessageData(Message.REQUEST, pieceIndex, 0, pieceSize));
						requestedPieces.add(pieceIndex);
						pieceWasRequested = true;
					} else if (pieceObj != null) {
						// At this point can either recursively call back on this,
						// which may or may not cause an infinite loop if the peer
						// has no pieces. Or can have some way of finding a Peer
						// that does have the piece and request it to that Peer.
						// Or can have some other way of finding a piece to request
						failedPieces.add(pieceObj);
					}
				}
			}
			for (PieceIndexCount triedPieceObj : failedPieces)
				failedPieces.add(triedPieceObj);

		} else {
			peer.send(new MessageData(Message.NOTINTERESTED));
		}
	}

	/**
	 * Possibly temporary helper for finding the number of
	 * connected peers that have the given piece. These should
	 * probably be cached somewhere else. returns Integer.MAX_VALUE
	 * if no peers have it since java's priority queue is a min
	 * heap and I want to put the pieces I have no information about
	 * at the end of the queue (unattainable pieces)
	 * @param  index the piece index
	 * @return       value for the priority queue
	 */
	protected int getPeerCountForPiece(int index) {
		int count = 0;
		for (Peer peer : connectedPeers.values()) {
			if (peer.getBitfield().get(index) == true)
				count++;
		}
		if (count == 0)
			count = Integer.MAX_VALUE;
		return count;
	}

	/**
	 * Updates the piecesToDownload priority queue when new info
	 * for a given piece comes in.
	 * 
	 * Oh man, this is cryptic. what's happening is that java
	 * has no update key function so I have to remove and then re-add
	 * the same thing to the priority queue with the new priority(key)
	 * value. Since I don't know the number of peers that had that piece
	 * beforehand, I am using only the piece index to remove it from the
	 * queue. the updated priority value comes from getPeerCountForPiece()
	 * 
	 * @param index index of the piece that needs updating
	 */
	protected synchronized void updateRarestPiece(int index) {
		PieceIndexCount piece = new PieceIndexCount(index, getPeerCountForPiece(index));
		if (piecesToDownload.contains(piece)) {
			piecesToDownload.remove(piece);
			piecesToDownload.add(piece);
		}
	}


	/**
	 * TorrentDelegate interface methods
	 */

	public void shutdown() {
		try {
			runQueue.putFirst(new Callable<Void>() {
				@Override
				public Void call() {
					chokeTimer.cancel();
					disconnectPeers();
					tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.STOPPED);
					isRunning = false;
					System.out.println("stopping torrent handler thread");
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void status(){
		System.out.format("downloaded: %d, uploaded: %d\n", getDownloaded(), getUploaded());
	}

	public ByteBuffer getHash() {
		return info.info_hash;
	}

	public void createIncomingPeer(Handshake peer_hs, Socket sock){
		Peer incPeer = Peer.peerFromHandshake(peer_hs, sock, this);
		incPeer.addObserver(odometer);

		if (incPeer != null && !incPeer.sock.isClosed() && incPeer.sock.isConnected()){
			if (!attemptingToConnectPeers.containsKey(incPeer.peer_id)) {
				attemptingToConnectPeers.put(incPeer.peer_id, incPeer);
				PeerRunnable.HS_StartAndReadRunnable runnable = new PeerRunnable.HS_StartAndReadRunnable(incPeer, peer_hs);
				(new Thread(runnable)).start();     
			}
		} else {
			System.err.println("Something fucked up, socket is closed on incPeer");
		}
	}


	/**
	 * "PeerDelegate" interface methods
	 */

	public void peerDidReceiveChoke(final Peer peer) { }

	public void peerDidReceiveUnChoke(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (didStart == false) {
						if (getDownloaded() != size)
							tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.STARTED);
						else
							tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.COMPLETED);
						didStart = true;
					}
					requestNextPiece(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveInterested(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					peer.send(new MessageData(Message.UNCHOKE));
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveNotInterested(final Peer peer) { }

	public void peerDidReceiveHave(final Peer peer, final int pieceIndex) {
		// update rarest piece
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					System.out.println("updating rarity for " + pieceIndex);
					updateRarestPiece(pieceIndex);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveBitfield(final Peer peer, final Bitfield bitfield) {
		// update rarest piece too
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					for (int i = 0; i < bitfield.numBits; i++) {
						if (bitfield.get(i) == true) {
							System.out.println("updating rarity for " + i);
							updateRarestPiece(i);
						}
					}
					System.out.println(piecesToDownload);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		peer.send(new MessageData(Message.INTERESTED));
	}

	public void peerDidReceiveRequest(final Peer peer, final int index, final int begin, final int length) {
		if (getLocalBitfield().get(index) == true) {
			// can then find piece in memory or let TorrentHandler
			// processs the request... the following does the former
			byte[] block = new byte[length];
			System.arraycopy(all_pieces[index], begin, block, 0, length);
			MessageData msg = new MessageData(Message.PIECE, index, begin, block);
			System.out.format("Sending PIECE index: %d, begin: %d, length: %d to peer %s\n",
				index, begin, length, peer.ip);
			peer.send(msg);
		}
	}

	public void peerDidReceivePiece(final Peer peer, final int index, final int begin, final byte[] block) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (pieceIsCorrect(index, block)) {
						if (localBitfield.get(index) == false) {
							saveTofile(new MessageData(Message.PIECE, index, begin, block));
							all_pieces[index] = block;
							localBitfield.set(index);
							try {
								sessionHandler.writeSession(localBitfield.array);
							} catch (Exception e) {
								e.printStackTrace();
							}
							incrementDownloaded(getPieceSize(index));
							System.out.format("downloaded %d out of %d (%.2f %%) (processed piece %d of size %d)\n",
								getDownloaded(), info.file_length, 100.0 * (double)getDownloaded() / info.file_length, index, getPieceSize(index));
						}
					} else {
						piecesToDownload.add(new PieceIndexCount(index, getPeerCountForPiece(index)));
					}
					requestedPieces.remove(index);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		requestNextPiece(peer);
	}

	public void peerDidReceiveCancel(final Peer peer, final int index, final int begin, final int length) {
		// peer.cancel(index, begin, length);
	}

	/**
	 * will be called by peer after a handshake exchange has been
	 * checked. Allows torrnent handler to know what peers have
	 * successfully connected.
	 * @param peer        the peer that has checked the handshakes
	 * @param peerIsLegit true if the handshake was correct,
	 *                    false if handshake was incorrect
	 */
	public void peerDidHandshake(final Peer peer, final Boolean peerIsLegit) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (peerIsLegit) {
						connectedPeers.put(peer.peer_id, peer);
						attemptingToConnectPeers.remove(peer.peer_id);
					} else {
						peer.shutdown();
					}
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidInitiateConnection(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					for (int i = 0; i < localBitfield.numBits; i++) {
						if (localBitfield.get(i) == true) {
							System.out.println("sending have " + i + " to peer " + peer.ip);
							peer.send(new MessageData(Message.HAVE, i));
						}
					}
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called by peer if it tried to connect but failed for any reason
	 * @param peer peer that failed to create a connection
	 */
	public void peerDidFailToConnect(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					System.err.println("Could not connect to peer " + peer.peer_id + " at ip: " + peer.ip);
					peer.shutdown();
					attemptingToConnectPeers.remove(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidDisconnect(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					attemptingToConnectPeers.remove(peer);
					connectedPeers.remove(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * getters for useful info
	 * the first two are also for the PeerDelegate interface
	 */

	public String getLocalPeerId() {
		return local_peer_id;
	}

	public TorrentInfo getTorrentInfo() {
		return info;
	}

	/**
	 * get the local bitfield.
	 * it will be a copy of the actual bitfield in order
	 * to avoid writing outside the TorrentHandler's thread
	 * @return a copy of the local bitfield
	 */
	public synchronized Bitfield getLocalBitfield() {
		return localBitfield.clone();
	}

	protected class OptimisticUnchokeTask extends TimerTask {
		@Override
		public void run() {
			try {
				runQueue.putLast(new Callable<Void>() {
					@Override
					public Void call() {
						System.out.println("OPTIMISTIC UNCHOKING task called");
						boolean slowPeerChoked = false;
						List<Map.Entry<String, Integer>> increasingDownloadPeers = odometer.poll();
						for (Map.Entry<String, Integer> peerEntry : increasingDownloadPeers) {
							if (connectedPeers.containsKey(peerEntry.getKey())) {
								Peer slowPeer = connectedPeers.get(peerEntry.getKey());
								if (slowPeer.getAmChoking() == false) {
									slowPeer.send(new MessageData(Message.CHOKE));
									slowPeerChoked = true;
									System.out.println("Choked peer " + slowPeer.ip);
									break;
								}
							}
						}
						if (slowPeerChoked) {
							List<Peer> unchokedAndInterestedPeers = new ArrayList<>();
							for (Peer peer : connectedPeers.values()) {
								if (peer.getAmChoking() && peer.getIsInterested()) {
									unchokedAndInterestedPeers.add(peer);
								}
							}
							if (unchokedAndInterestedPeers.size() > 0) {
								int randomIndex = (new Random()).nextInt(unchokedAndInterestedPeers.size());
								Peer randomPeer = unchokedAndInterestedPeers.get(randomIndex);
								randomPeer.send(new MessageData(Message.UNCHOKE));
								System.out.println("Unchoked peer " + randomPeer.ip);
							}
						}
						return null;
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	protected OptimisticUnchokeTask optimisticUnchokeTask = new OptimisticUnchokeTask();

	/**
	 * Start torrent handler. Which will communicate with the tracker
	 * and parse through the tracker response to create a connection to
	 * the peers that begin with "-RU".
	 */
	@SuppressWarnings("unchecked")
	public void run() {
		Tracker.MessageType event;
		if (size == getDownloaded())
			event = Tracker.MessageType.STARTED;
		else
			event = Tracker.MessageType.UNDEFINED;
		Map<ByteBuffer, Object> decodedData = tracker.getTrackerResponse(uploaded, downloaded, event);
		ToolKit.print(decodedData);
		if (decodedData != null && !finished) {
			Object value = decodedData.get(Tracker.KEY_PEERS);
			ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)value;
			// ToolKit.print(peers);
			if (peers != null) {
				for (Map<ByteBuffer, Object> map_peer : peers) {
					ByteBuffer ip = (ByteBuffer)map_peer.get(Tracker.KEY_IP);
					if (ip != null) {
						// String new_peer_ip = new String(ip.array());
						// if (new_peer_ip.compareTo("128.6.171.130") == 0 ||
						// 	new_peer_ip.compareTo("128.6.171.131") == 0)
						// {
						// }
						// establish a connection with this peer
						Peer client = Peer.peerFromMap(map_peer, this);
						client.addObserver(odometer);
						attemptingToConnectPeers.put(client.peer_id, client);
						client.startThreads();
					}
				}
			} else {
				System.err.println("Could not find key PEERS in decoded tracker response");
			}
		} else if (finished) {
			System.out.println("Seeding.");
		} else {
			System.err.println("Tracker response came back empty, please try again.");
		}

		chokeTimer.scheduleAtFixedRate(optimisticUnchokeTask, 30000, 30000);

		// Now start consuming the queue, calling each object/
		// block of code put in the queue
		Callable<Void> block = null;
		try {
			while (isRunning && (block = runQueue.takeFirst()) != null) {
				block.call();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("TorrentHandler closing");
	}
}
