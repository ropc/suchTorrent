import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.security.*;
import GivenTools.*;

public class TorrentHandler implements PeerDelegate {
	public final TorrentInfo info;
	public Tracker tracker;
	public final String escaped_info_hash;
	public String local_peer_id;
	public int uploaded;
	public int downloaded;
	public int size;
	public Writer fileWriter;
	public MessageData[] all_pieces;


	public static TorrentHandler create(String torrentFilename, String saveFileName) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(torrentFilename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, SuchTorrent.generatePeerId(), saveFileName);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("could not create torrent handler");
		}
		return newTorrent;
	}

	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String peer_id, String saveFileName) throws IOException {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		local_peer_id = peer_id;
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
		tracker = new Tracker(escaped_info_hash, peer_id, info.announce_url.toString(), size);
		all_pieces = new MessageData[info.piece_hashes.length];
		fileWriter = new Writer(saveFileName, info.piece_length);
	}

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

	public void saveTofile() {
		if (downloaded == info.file_length)
			System.out.println("Downloaded everything =)");
		else
			System.out.println("didnt download everything?");
		for (MessageData pieceData : all_pieces) {
			fileWriter.writeMessage(pieceData.message);
		}
	}

	public int getPieceSize(int pieceIndex) {
		int pieceSize;
		if (pieceIndex == info.piece_hashes.length - 1)
			pieceSize = size % info.piece_length;
		else
			pieceSize = info.piece_length;
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
	public Boolean peerDidReceiveMessage(Peer peer, MessageData message) {
		Boolean continueReading = false;
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
					int pieceSize = getPieceSize(nextPiece);
					downloaded += pieceSize;
				} else {
					System.out.println("piece " + message.pieceIndex + " was incorrect.");
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
				continueReading = true;
			} else {
				continueReading = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("notifying tracker that download will stop");
			tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.STOPPED);
			continueReading = false;
		}
		return continueReading;
	}

	public void peerDidHandshake(Peer peer, Boolean peerIsLegit) {
		if (peerIsLegit) {
			peer.startReading();
		} else {
			System.err.println("handshake with peer " + peer.peer_id + " failed.");
			peer.disconnect();
		}
	}

	public void peerDidFailToConnect(Peer peer) {
		System.err.println("Could not connect to peer " + peer.peer_id + " at ip: " + peer.ip);
	}

	public void start() {
		Map<ByteBuffer, Object> decodedData = tracker.getTrackerResponse(uploaded, downloaded);
		ToolKit.print(decodedData);
		ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)decodedData.get(Tracker.KEY_PEERS);
		// ToolKit.print(peers);
		for (Map<ByteBuffer, Object> map_peer : peers) {
			ByteBuffer id = (ByteBuffer)map_peer.get(Tracker.KEY_PEER_ID);
			if (id != null) {
				String new_peer_id = new String(id.array());
				if (new_peer_id.substring(0, 3).compareTo("-RU") == 0)
				{
					// establish a connection with this peer
					Peer client = Peer.peerFromMap(map_peer, this);
					client.start(info, local_peer_id);
				}
			}
		}
	}
}
