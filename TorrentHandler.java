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

	public final static ByteBuffer KEY_COMPLETE = ByteBuffer.wrap(new byte[] { 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e'});
	public final static ByteBuffer KEY_DOWNLOADED = ByteBuffer.wrap(new byte[] { 'd', 'o', 'w', 'n', 'l', 'o', 'a', 'd', 'e', 'd' });
	public final static ByteBuffer KEY_INCOMPLETE = ByteBuffer.wrap(new byte[] { 'i', 'n', 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });
	public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public final static ByteBuffer KEY_MIN_INTERVAL = ByteBuffer.wrap(new byte[] { 'm', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public final static ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', 's' });
	public final static ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] { 'i', 'p' });
	public final static ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public final static ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' });


	public static TorrentHandler buildTorrent(String filename) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, SuchTorrent.generatePeerId());
		} catch (Exception e) {
			System.out.println("could not create");
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
		try {
			byte[] requestMsg;
			switch (message.type) {
				case BITFIELD:
					peer.send(Message.encodeMessage(Message.INTERESTED));
					System.out.println("sent that i'm interested");
					break;
				case UNCHOKE:
					requestMsg = Message.encodeMessage(Message.REQUEST, Message.buildRCTail(0, 0, info.piece_length));
					peer.send(requestMsg);
					System.out.println("sent request for piece 0");
					for (byte muhByte : requestMsg)
						System.out.print(muhByte + " ");
					System.out.println();
					break;
				case PIECE:
					int nextPiece;
					if (pieceIsCorrect(message)) {
						requestMsg = Message.encodeMessage(Message.HAVE, Message.buildHaveTail(message.pieceIndex));
						peer.send(requestMsg);
						System.out.println("sent HAVE piece " + message.pieceIndex + " to peer");
						nextPiece = message.pieceIndex + 1;
					} else {
						System.out.println("piece " + message.pieceIndex + " was incorrect.");
						nextPiece = message.pieceIndex;
					}
					requestMsg = Message.encodeMessage(Message.REQUEST, Message.buildRCTail(nextPiece, 0, info.piece_length));
					peer.send(requestMsg);
					System.out.println("sent request for piece " + nextPiece);
					for (byte muhByte : requestMsg)
						System.out.print(muhByte + " ");
					System.out.println();
					break;
				default:
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void peerDidHandshake(Peer peer, Boolean peerIsLegit) {
		if (peerIsLegit) {
			peer.startReading();
		} else {
			System.out.println("handshake with peer " + peer.peer_id + " failed.");
		}
	}

	public void start() {
		Map<ByteBuffer, Object> decodedData = tracker.getTrackerResponse(uploaded, downloaded);
		ToolKit.print(decodedData);
		ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)decodedData.get(TorrentHandler.KEY_PEERS);
		// ToolKit.print(peers);
		for (Map<ByteBuffer, Object> map_peer : peers) {
			ByteBuffer id = (ByteBuffer)map_peer.get(TorrentHandler.KEY_PEER_ID);
			if (id != null) {
				String new_peer_id = new String(id.array());
				if (new_peer_id.substring(0, 3).compareTo("-RU") == 0)
				{
					// establish a connection with this peer
					// this should be handled by another class

					// for now, just printing
					// System.out.println(new_peer_id);
					Peer client = Peer.peerFromMap(map_peer);
					client.delegate = this;
					client.handshake(info, local_peer_id);
				}
			}
		}
	}
}
