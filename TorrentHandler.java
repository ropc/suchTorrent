import java.nio.file.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

class TorrentHandler {
	public final TorrentInfo info;
	public final String escaped_info_hash;
	public String peer_id;
	public int uploaded;
	public int downloaded;
	public int size;

	public static TorrentHandler buildTorrent(String filename) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			System.out.println(newInfo.announce_url);
			newTorrent = new TorrentHandler(newInfo, newInfoHash, SuchTorrent.generatePeerId());

		} catch (Exception e) {
			System.out.println("could not create");
		}
		return newTorrent;
	}

	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String peer_id) {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		this.peer_id = peer_id;
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
	}

	public HttpURLConnection getInitialTrackerRequest(String peer_id, int port) {
		StringBuilder urlString = new StringBuilder(info.announce_url.toString());
		urlString.append("?info_hash=" + escaped_info_hash);
		try {
			urlString.append("&peer_id=" + URLEncoder.encode(peer_id, "ISO-8859-1"));
			urlString.append("&port=" + port);
			urlString.append("&uploaded=" + uploaded);
			urlString.append("&downloaded=" + downloaded);
			urlString.append("&left=" + size);
			URL url = new URL(urlString.toString());
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			return urlConnection;
		} catch (Exception e) {
			return null;
		}
	}

	public Map<String, Object>getTrackerResponse() {
		HttpURLConnection connection = getInitialTrackerRequest(peer_id, 6881);
		byte[] content = new byte[connection.getContentLength()];
		try {
			connection.getInputStream().read(content);
			connection.getInputStream().close();
			connection.disconnect();
			return (Map<String, Object>)Bencoder2.decode(content);
		} catch (Exception e) {
			return null;
		}
	}
}