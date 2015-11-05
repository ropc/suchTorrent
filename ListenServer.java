import java.nio.*;
import java.net.*;
import java.util.concurrent.*;
import java.io.*;

public final class ListenServer implements Runnable{

   private static ListenServer _instance;

   private ServerSocket listenSocket;
   private ConcurrentMap<ByteBuffer,TorrentHandler> torrentMap;
   private Boolean isActive;

   private ListenServer (ConcurrentMap<ByteBuffer, TorrentHandler> torrents){
      torrentMap = torrents;
      isActive = true;
      for (int port = 6881; listenSocket == null && port  < 6900; port++){
         try{
            listenSocket = new ServerSocket(port);
         }
         catch (Exception e){
            System.err.println(e.toString());
            System.err.println("Could not use port " + port);
         }
      }
   }
   
   public static ListenServer create(ConcurrentMap<ByteBuffer, TorrentHandler> torrents){
      if (_instance != null)
      {
         System.out.println("ListenServer is already instantiated.");
         return _instance;
      }
      _instance = new ListenServer(torrents);
      return _instance;
   }

   public int getListenPort(){
      return listenSocket.getLocalPort();
   }

   public void run() {
      Handshake hs = null;

      System.out.println("Listener Thread is running!"); 
      int i = 0;

      while(i < 5 && isActive){
         System.out.println("Waiting for connection...");
         
         try(Socket sock = listenSocket.accept()){
            System.out.println("Got incoming connection from " + sock.getInetAddress().toString() + " on port " + sock.getPort());
            try (DataInputStream in = new DataInputStream(sock.getInputStream())){
               byte pstrlen = in.readByte();
               int length = (int)pstrlen + 49;
               byte[] peer_bytes = new byte[length];
               peer_bytes[0] = pstrlen;
               in.readFully(peer_bytes, 1, length - 1);
               hs = Handshake.decode(peer_bytes);
              
               if (torrentMap.containsKey(hs.info_hash)){
                  TorrentDelegate torr = torrentMap.get(hs.info_hash);
                  System.out.print("Found peer: " + hs.peer_id + " for torrent with hash: ");
                  for(int j = 0; j < 20; j++){
                     System.out.print(hs.info_hash.get(j));
                  }
                  System.out.println();
                  //torr.createIncomingPeer(sock, hs);
               }
               else{
                  System.err.println("Peer connected with unknown info-hash!");
               }         
            }
            catch(EOFException e){
               System.err.println("Reached end of Input Stream unexpectedly: " + sock.getInetAddress() + ", " + sock.getPort());
               continue;
            }
            catch (Exception e){
               System.err.println(e.toString());
            }
            i++;
            System.out.println("Closed Socket: " + sock.isClosed());
         }
         catch(Exception e){
            e.toString();
         }
      }
      System.out.println("Finishing thread..."); 
   }

   public void stop() {
      isActive = false;
      try {
         listenSocket.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

}
