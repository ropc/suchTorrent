import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import GivenTools.*;

class SuchTorrent {
	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder("RU-");
		for (int i = 0; i < 17; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		try {
			return URLEncoder.encode(sb.toString(), "UTF-8");
		} catch (Exception e) {
			return null;
		}	
	}

	public static ServerSocket getOpenServerSocket() {
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
		TorrentHandler myTorrent = null;
		if (args.length == 1) {
			myTorrent = TorrentHandler.buildTorrent(args[0]);
		}

		if (myTorrent != null) {
			try {		
				Map<ByteBuffer, Object> decodedData = myTorrent.getTrackerResponse();
				ToolKit.print(decodedData);
				ArrayList peers = (ArrayList)decodedData.get(TorrentHandler.KEY_PEERS);
				ToolKit.print(peers);

			} catch (Exception e) {
				System.out.println("whatthefuck: " + e);
				e.printStackTrace();	
			}
		}
	}
}