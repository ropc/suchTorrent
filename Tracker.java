import java.nio.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

public class Tracker{
	private static String escaped_info_hash; // The info hash of the file [to be passed to the tracker and to the peers]
	private static String peer_id; // The peer_id for this user
	private int interval; //The time interval between get requests expected by the tracker
	private static int size;//Total size of the file being downloaded
	private static String URL; //The url of the tracker
	private int uploaded; // The amount that has been uploaded so far [since start message sent to tracker]
	private int downloaded;// The amount that has been downloaded so far [since start message sent to tracker]
	public enum MessageType{GET,STARTED,STOPPED,COMPLETED} //The type of message that can be sent to the tracker
	public MessageType Event; //The type of message that is to be sent to the tracker

	public final static ByteBuffer KEY_COMPLETE = ByteBuffer.wrap(new byte[] { 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e'});
	public final static ByteBuffer KEY_DOWNLOADED = ByteBuffer.wrap(new byte[] { 'd', 'o', 'w', 'n', 'l', 'o', 'a', 'd', 'e', 'd' });
	public final static ByteBuffer KEY_INCOMPLETE = ByteBuffer.wrap(new byte[] { 'i', 'n', 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });
	public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public final static ByteBuffer KEY_MIN_INTERVAL = ByteBuffer.wrap(new byte[] { 'm', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public final static ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', 's' });
	public final static ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] { 'i', 'p' });
	public final static ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public final static ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' });

	public  Tracker(String einfo, String peerid, String aURL,int filesize){ //The initializer for a Tracker
		escaped_info_hash = einfo;
		peer_id = peerid;
		URL=aURL;
		size=filesize;
		Event=MessageType.STARTED;
		return;
	}
	
	
	private HttpURLConnection TalkToTracker(int port){ //Build a message to the Tracker. Return the url connection.
		StringBuilder urlString = new StringBuilder(URL);
		urlString.append("?info_hash=" + escaped_info_hash);
		try {
			urlString.append("&peer_id=" + URLEncoder.encode(peer_id, "ISO-8859-1"));
			urlString.append("&port=" + port);
			urlString.append("&uploaded=" + uploaded);
			urlString.append("&downloaded=" + downloaded);
			urlString.append("&left=" + (size-downloaded));
			switch(Event){
				case STARTED: {urlString.append("&event=started");break;}		//optional parameters specifying the type of message
				case COMPLETED: {urlString.append("&event=completed");break;}
				case STOPPED: {urlString.append("&event=stopped");break;}
			}
			URL url = new URL(urlString.toString());
			System.out.println(url);
			HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setRequestMethod("GET");
			return urlConnection;
		} catch (Exception e) {
			return null;
		}
	}
	
	public Map<ByteBuffer, Object> getTrackerResponse(int Cuploaded, int Cdownloaded){//Send a message to the tracker and wait on a response. Return the decoded response.
		uploaded = Cuploaded;		
		downloaded = Cdownloaded;		//Update the uploaded and downloaded amounts
		Map<ByteBuffer, Object> retval;	//The Map that will be returned at the end of the method.
		
		boolean flag=true;
		int i=6881;
	
		HttpURLConnection connection = TalkToTracker(i);//begin by trying to connect at the lowest port number.
		try{connection.connect();}							//Try and connect to this version of the connection
		catch(Exception e){flag=false;}					//if it doesn't work, set the flag to false and enter the while loop [goes to false if connection is null as well]
		
		while(++i<=6889&&(connection==null||!flag)){//try connection until you either get a connection or run out of valid ports to try on
			connection = TalkToTracker(i);			
			flag=true;
			try{connection.connect();}
			catch(Exception e){flag = false;}
			}
		if(connection==null){return null;}
		
		byte[] content = new byte[connection.getContentLength()];
		try {
			connection.getInputStream().read(content);	//read in the message sent by the tracker
			connection.getInputStream().close();
			connection.disconnect();
			retval = (Map<ByteBuffer, Object>)Bencoder2.decode(content); //Decode the message
			interval = (Integer)retval.get(KEY_INTERVAL); //Set the interval
			return retval;
		} catch (Exception e) {
			return null;
		}
		
	}
	
	public void StopCall(int Cuploaded, int Cdownloaded){
		int i=6881;
		
		uploaded = Cuploaded;
		downloaded = Cdownloaded;//Update the uploaded and downloaded amounts
		
		
		Event=MessageType.STOPPED;	//Change the message type to stopped
		boolean flag=true;
		
		HttpURLConnection connection = TalkToTracker(i);//Send a stopped message to the tracker
		try{connection.connect();}			//As above try once then if it fails try in a loop
		catch(Exception e){flag = false;}
			
		
		while(++i<=6889&&(connection==null||!flag)){//try connection until you either get a connection or run out of valid ports to try on
			connection = TalkToTracker(i);
			flag=true;
			try{connection.connect();}
			catch(Exception e){flag=false;}
			}
		if(connection==null){return;}
		
		}
		
}
