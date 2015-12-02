/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.net.*;
import java.util.*;
import java.nio.*;
import GivenTools.*;
import java.util.concurrent.*;

public class RUBTClient {

	public static final String peerId = generatePeerId();
	private static int port;
	/**
	 * returns a randomly generated peer id to be used for
	 * communication with peers/tracker
	 * @return the peer id
	 */
	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		return sb.toString();	
	}

	public static int getListenPort(){
		return port;
	}

	/**
	 * main method for BitTorrent client.
	 * creates a TorrentHandler object that will handle the download
	 * for a given torrent.
	 * @param args command line arguments
	 *             these should be torrentFileName saveFileName
	 */
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		Boolean isReceivingInput = true;

		System.out.println("Peer ID is: " + peerId);
		ListenServer server;
		ConcurrentMap<ByteBuffer, TorrentHandler> torrentMap;

		torrentMap = new ConcurrentHashMap<ByteBuffer, TorrentHandler>();
		server = ListenServer.create(torrentMap);
		port = server.getListenPort();
		System.out.println("Listening on port: " + port);

		Thread listener =  new Thread(server);
		listener.start();

		TorrentHandler myTorrent = null;

		if (args.length == 2) {
			myTorrent = TorrentHandler.create(args[0], args[1]);
			if (myTorrent != null) {
				torrentMap.put(myTorrent.info.info_hash, myTorrent);
				new Thread(myTorrent).start();
			} else {
				System.err.println("Couldn't start torrent: " + args[0]);
				isReceivingInput = false;
				server.shutdown();
			}
		} else {
			System.err.println("Client takes in exactly 2 arguments: TorrentFile, SaveFileName");
			isReceivingInput = false;
			server.shutdown();
		}

		while (isReceivingInput && sc.hasNextLine()) {
			String input = sc.nextLine();
			
			if (input.equalsIgnoreCase("exit"))
				isReceivingInput = false;
			else if (input.equalsIgnoreCase("status"))
				myTorrent.status();
		}
		server.shutdown();
		myTorrent.shutdown();
	}
}
