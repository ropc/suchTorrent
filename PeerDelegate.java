/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
public interface PeerDelegate {
	public void peerDidHandshake(Peer peer, Boolean peerIsLegit);
	public Boolean peerDidReceiveMessage(Peer peer, MessageData message);
	public void peerDidFailToConnect(Peer peer);
}
