import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.net.*;
import java.util.Random;
import GivenTools.*;

class SuchTorrent {

	public static TorrentInfo getTorrentInfo(String filename) {
		try {
			TorrentInfo torrentFileInfo = new TorrentInfo(Files.readAllBytes(Paths.get(filename)));
			return torrentFileInfo;
		} catch (Exception e) {
			return null;
		}
	}

	// public static bytes[] httpGET(String url) {
	// 	try {
	// 		HttpURLConnection con = (HttpURLConnection) (new URL(url)).openConnection();
	// 		con.setRequestMethod("GET");
	// 		con.getInputStream()
	// 	} catch (Exception e) {
	// 		return null;
	// 	}
	// }

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder("RU-");
		for(int i = 0; i < 17; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		try {
			return URLEncoder.encode(sb.toString(), "UTF-8");
		} catch (Exception e) {
			return null;
		}	
	}

	public static ServerSocket getOpenSocket() {
		ServerSocket newSocket = null;
		for (int port = 6881; newSocket == null && port <= 6889; port++) {
			try {
				newSocket = new ServerSocket(port);
			} catch (Exception e) {
				System.out.println("Could not open port" + port);
			}
		}
		return newSocket;
	}


	public static void main(String[] args) {
		TorrentInfo torrentFileInfo = null;
		if (args.length == 1) {
			torrentFileInfo = getTorrentInfo(args[0]);
		}

		if (torrentFileInfo != null) {
			System.out.println(torrentFileInfo.announce_url);
			try {
				String urlString = new String(torrentFileInfo.info_hash.array(), "UTF-8");
				// String urlString = torrentFileInfo.info_hash.asCharBuffer().toString();
				// System.out.println(torrentFileInfo.info_hash.asCharBuffer());
				System.out.println(urlString);
				System.out.println(URLEncoder.encode(urlString, "UTF-8"));
				System.out.println(bytesToHex(torrentFileInfo.info_hash.array()));
				System.out.println(bytesToHex(urlString.getBytes()));

				String id = generatePeerId();
				System.out.println(id);


			} catch (Exception e) {
				System.out.println("whatthefuck");	
			}
		}
	}
}