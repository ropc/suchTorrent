import java.util.Observable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.HashMap;
import java.util.SortedSet;

public class SpeedTestDotNet extends Observable {
   
   public HashMap<String, int> deltaDownloaded;
   public HashMap<String, int> deltaUploaded;
   public Timer timer;

   class Poll extends TimerTask{
      
      public void run(){
         
      }

   }


   public SpeedTestDotNet(){
      Timer =  
   }


   public synchronized Set<Map.Entry<String, int>> poll(){
      
   }

   public synchronized int increment(String peerID, int quantity, HashMap<String, int> deltaMap){
      return deltaMap.put(peerID, deltaMap.get(peerID) + quantity);
   }



}
