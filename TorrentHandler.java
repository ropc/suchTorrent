import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

public class TorrentHandler {
	public final TorrentInfo info;
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
	}

	public HttpURLConnection getInitialTrackerRequest(int port) {
		StringBuilder urlString = new StringBuilder(info.announce_url.toString());
		urlString.append("?info_hash=" + escaped_info_hash);
		try {
			urlString.append("&peer_id=" + URLEncoder.encode(local_peer_id, "ISO-8859-1"));
			urlString.append("&port=" + port);
			urlString.append("&uploaded=" + uploaded);
			urlString.append("&downloaded=" + downloaded);
			urlString.append("&left=" + size);
			URL url = new URL(urlString.toString());
			System.out.println(url);
			HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setRequestMethod("GET");
			return urlConnection;
		} catch (Exception e) {
			return null;
		}
	}

	public Map<ByteBuffer, Object>getTrackerResponse() {
		HttpURLConnection connection = getInitialTrackerRequest(6881);
		byte[] content = new byte[connection.getContentLength()];
		try {
			connection.getInputStream().read(content);
			connection.getInputStream().close();
			connection.disconnect();
			return (Map<ByteBuffer, Object>)Bencoder2.decode(content);
		} catch (Exception e) {
			return null;
		}
	}

	public Boolean peerDidReceiveMessage(Peer peer, byte[] message) {
		System.out.println("torrent handler can see that peer '" + peer.peer_id + "' has sent message:");
		for (byte i : message) {
			System.out.print(i + " ");
		}
		System.out.println();
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
		Map<ByteBuffer, Object> decodedData = getTrackerResponse();
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
