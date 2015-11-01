import java.nio.*;
import java.net.*;
import java.util.concurrent.*;
import java.io.IOException;

public final class ListenServer implements Runnable{

   private static ListenServer _instance;

   private ServerSocket listenSocket;
   private ConcurrentMap<ByteBuffer,TorrentHandler> torrentMap;

   private ListenServer (ConcurrentMap<ByteBuffer, TorrentHandler> torrents){
      torrentMap = torrents;
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
      Socket sock;
      System.out.println("Listener Thread is running!"); 
      try {
         sock = listenSocket.accept();
      
         System.out.println("Got incoming connection from " + sock.getInetAddress().toString() + " on port " + sock.getPort());
         sock.close();
         System.out.println("Closed? " + sock.isClosed());
         
         listenSocket.close();
      }
      catch (IOException e){
         System.err.println(e.getMessage());
      }
      System.out.println("Listen Closed? " + listenSocket.isClosed());
      
      System.out.println("Finishing thread..."); 
   }

}
