import java.nio.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

public class Tracker{
	private static String escaped_info_hash;
	private static String peer_id;
	private int interval;
	private static int size;
	private static String URL;
	private int uploaded;
	private int downloaded;
	public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });

	
	public  Tracker(String einfo, String peerid, String aURL,int filesize){
		escaped_info_hash = einfo;
		peer_id = peerid;
		URL=aURL;
		size=filesize;
		return;
	}
	
	
	private HttpURLConnection TalkToTracker(int port){
		StringBuilder urlString = new StringBuilder(URL);
		urlString.append("?info_hash=" + escaped_info_hash);
		try {
			urlString.append("&peer_id=" + URLEncoder.encode(peer_id, "ISO-8859-1"));
			urlString.append("&port=" + port);
			urlString.append("&uploaded=" + uploaded);
			urlString.append("&downloaded=" + downloaded);
			urlString.append("&left=" + (size-downloaded));
			URL url = new URL(urlString.toString());
			System.out.println(url);
			HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setRequestMethod("GET");
			return urlConnection;
		} catch (Exception e) {
			return null;
		}
	}
	
	public Map<ByteBuffer, Object> getTrackerResponse(int Cuploaded, int Cdownloaded){
		uploaded = Cuploaded;
		downloaded = Cdownloaded;
		Map<ByteBuffer, Object> retval;	
		HttpURLConnection connection = TalkToTracker(6881) ;
		int i=6881;
		while(++i<=6889&&connection!=null){
			connection = TalkToTracker(i);
		}
		if(connection==null){return null;}
		
		byte[] content = new byte[connection.getContentLength()];
		try {
			connection.getInputStream().read(content);
			connection.getInputStream().close();
			connection.disconnect();
			retval = (Map<ByteBuffer, Object>)Bencoder2.decode(content);
			interval = (Integer)retval.get(KEY_INTERVAL);
			return retval;
		} catch (Exception e) {
			return null;
		}
		
	}
		
	}
