import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.security.*;
import GivenTools.*;

public class TorrentHandler {
	public final TorrentInfo info;
	public Tracker tracker;
	public final String escaped_info_hash;
	public String local_peer_id;
	public int uploaded;
	public int downloaded;
	public int size;
	public MessageData[] all_pieces;


	public static TorrentHandler buildTorrent(String filename) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, SuchTorrent.generatePeerId());
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("could not create torrent handler");
		}
		return newTorrent;
	}

	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String peer_id) {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		local_peer_id = peer_id;
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
		tracker = new Tracker(escaped_info_hash, peer_id, info.announce_url.toString(), size);
		all_pieces = new MessageData[info.piece_hashes.length];
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

	public Boolean peerDidReceiveMessage(Peer peer, MessageData message) {
		Boolean continueReading = false;
		try {
			MessageData requestMsg = null;
			String outputString = "";
			if (message.type == Message.BITFIELD) {
				requestMsg = new MessageData(Message.INTERESTED);
				outputString = "sening INTERESTED";
			} else if (message.type == Message.UNCHOKE) {
				requestMsg = new MessageData(Message.REQUEST, 0, 0, info.piece_length);
				outputString = "sening request for piece 0";
			} else if (message.type == Message.PIECE) {
				int nextPiece;
				if (pieceIsCorrect(message)) {
					requestMsg = new MessageData(Message.HAVE, message.pieceIndex);
					peer.send(requestMsg);
					System.out.println("sening HAVE piece " + message.pieceIndex + " to peer");
					requestMsg = null;
					all_pieces[message.pieceIndex] = message;
					nextPiece = message.pieceIndex + 1;
				} else {
					System.out.println("piece " + message.pieceIndex + " was incorrect.");
					nextPiece = message.pieceIndex;
				}
				if (nextPiece < info.piece_hashes.length) {
					int pieceSize;
					if (nextPiece == info.piece_hashes.length - 1)
						pieceSize = size % info.piece_length;
					else
						pieceSize = info.piece_length;
					requestMsg = new MessageData(Message.REQUEST, nextPiece, 0, pieceSize);
					outputString = "sening request for piece " + nextPiece;
				} else {
					System.out.println("done downloading, disconnecting from peer: " + peer.peer_id);
					peer.disconnect();
				}
			}
			if (requestMsg != null) {
				peer.send(requestMsg);
				System.out.println(outputString);
				for (byte muhByte : requestMsg.message)
					System.out.print(muhByte + " ");
				System.out.println();
				continueReading = true;
			} else {
				continueReading = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
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
					Peer client = Peer.peerFromMap(map_peer);
					client.delegate = this;
					client.handshake(info, local_peer_id);
				}
			}
		}
	}
}
