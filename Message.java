import java.nio.*;
import java.util.*;

public class Message {

	private static byte[] message(int lengthPrefix, int messageId) {
		byte[] message = new byte[4 + lengthPrefix];
		byte[] lengthPrefixArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(lengthPrefix).array();
		System.arraycopy(lengthPrefixArray, 0, message, 0, 4);
		message[4] = (byte)messageId;
		return message;
	}

	public static byte[] INTERESTED = message(1, 2);
	public static byte[] NOT_INTERESTED = message(1, 3);

	// public static read(byte[] headerBytes, Data)

	public static byte[] request(int index, int begin, int length) {
		byte[] message = message(13, 6);
		byte[] indexArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
		byte[] beginArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(begin).array();
		byte[] lengthArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array();
		System.arraycopy(indexArray, 0, message, 5, 4);
		System.arraycopy(beginArray, 0, message, 9, 4);
		System.arraycopy(lengthArray, 0, message, 13, 4);
		return message;
	}
}