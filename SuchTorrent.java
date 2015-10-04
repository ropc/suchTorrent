import java.net.*;
import java.util.*;
import GivenTools.*;

public class SuchTorrent {
	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		System.out.println("generated peer id: " + sb.toString());
		return sb.toString();	
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
			myTorrent.start();
		}
	}
}
