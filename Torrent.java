import java.nio.file.*;
import java.net.*;
import GivenTools.*;

class Torrent {
	public final TorrentInfo info;
	public final String escaped_info_hash;

	public static Torrent buildTorrent(String filename) {
		Torrent newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			System.out.println(newInfo.announce_url);

			newTorrent = new Torrent(newInfo, newInfoHash);

		} catch (Exception e) {
			System.out.println("could not create");
		}
		return newTorrent;
	}

	protected Torrent(TorrentInfo info, String escaped_info_hash) {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
	}

	public HttpURLConnection getInitialTrackerRequest(String peer_id, int port) {
		StringBuilder urlString = new StringBuilder(info.announce_url.toString());
		urlString.append("?info_hash=" + escaped_info_hash);
		try {
			urlString.append("?peer_id=" + URLEncoder.encode(peer_id, "ISO-8859-1"));
			urlString.append("?port=" + port);
			URL url = new URL(urlString.toString());
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			return urlConnection;
		} catch (Exception e) {
			return null;
		}
	}
}