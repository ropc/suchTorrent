/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 **/
import java.nio.ByteBuffer;
import java.net.Socket;
import java.io.DataInputStream;

public interface TorrentDelegate {

   public void shutdown();
   public void status();
   public ByteBuffer getHash();
   public void createIncomingPeer(Handshake peer_hs, Socket sock, DataInputStream in);

}
