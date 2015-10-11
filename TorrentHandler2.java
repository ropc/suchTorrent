import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

public class TorrentHandler2 implements PeerDelegate {
	public final TorrentInfo info;
	public final String escaped_info_hash;
	public Peer localPeer;
	public String peer_id;
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


	public static TorrentHandler2 buildTorrent(String filename) {
		TorrentHandler2 newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler2(newInfo, newInfoHash, SuchTorrent.generatePeerId());
		} catch (Exception e) {
			System.out.println("could not create");
		}
		return newTorrent;
	}

	protected TorrentHandler2(TorrentInfo info, String escaped_info_hash, String peer_id) {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		this.peer_id = peer_id;
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
	}


	public void peerDidHandshake(Peer peer, Boolean legit) {
		System.out.println("O WOW SUCH JAVA");
	}

	public void start() {
		Tracker aTracker = new Tracker(escaped_info_hash,peer_id,info.announce_url.toString(),size);
		Map<ByteBuffer, Object> decodedData = aTracker.getTrackerResponse(uploaded,downloaded);
		ToolKit.print(decodedData);
		ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)decodedData.get(TorrentHandler2.KEY_PEERS);
		// ToolKit.print(peers);
		for (Map<ByteBuffer, Object> map_peer : peers) {
			ByteBuffer id = (ByteBuffer)map_peer.get(TorrentHandler2.KEY_PEER_ID);
			Writer aWrite = new Writer("test14");
			ByteBuffer copy =id.duplicate();	
			aWrite.WriteData(0,2,8,copy);
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
					client.handshake(info, peer_id);
				}
			}
		}
	}
}
