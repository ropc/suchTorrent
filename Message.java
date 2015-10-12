import java.nio.*;
import java.util.*;

public enum Message {

   
	KEEPALIVE      (new byte[]{0, 0, 0, 0}),
	CHOKE          (new byte[]{0, 0, 0, 1, 0}),
	UNCHOKE        (new byte[]{0, 0, 0, 1, 1}),
	INTERESTED     (new byte[]{0, 0, 0, 1, 2}),
	NOTINTERESTED  (new byte[]{0, 0, 0, 1, 3}),
	HAVE           (new byte[]{0, 0, 0, 5, 4}),
	BITFIELD       (new byte[]{0, 0, 0, 1, 5}),   
	REQUEST        (new byte[]{0, 0, 0, 13, 6}),
	PIECE          (new byte[]{0, 0, 0, 9, 7}),
	CANCEL         (new byte[]{0, 0, 0, 13, 8});

	private final byte[] messageHead;

	Message (byte[] messageHead){
	  this.messageHead = messageHead;
	}

	/**
	* Returns what type of Message is being handled, also 
	* checks for basic syntax errors.
	*
	* @param recMessage    Any peer wire message, received as a byte array 
	*/
	public byte[] getMessageHead(){
	  byte[] copy = new byte[this.messageHead.length];
	  System.arraycopy(this.messageHead, 0, copy, 0, copy.length);
	  return copy;
	}

	public static Message getType(byte[] recMessage) throws IllegalArgumentException{
	  
	  //First five are easy to verify
	  for (int i = 0; i < 5; i++){
	     if (Arrays.equals(recMessage, Message.values()[i].messageHead))
	        return Message.values()[i];
	  }

	  //Make sure lengths and IDs are valid   
	  if (recMessage.length < 5 || recMessage[4] < 0 || recMessage[4] > 9)
	     throw new IllegalArgumentException("Bad message; invalid message ID of " + recMessage[4]);
	  else if (recMessage.length != ByteBuffer.wrap(recMessage).getInt() + 4){
	     throw new IllegalArgumentException("Bad message; prefix length = " + ByteBuffer.wrap(recMessage).getInt() + ", actual length = " + (recMessage.length - 4));
	  }
	  
	 return Message.values()[recMessage[4] + 1];
	}

	public static byte[] encodeMessage(Message type) throws RuntimeException {
		return encodeMessage(type, null);
	}

	public static byte[] encodeMessage(Message type, byte[] messageTail) throws RuntimeException{
	  byte[] outMessage = null;
	  
	  switch (type) {
	     case KEEPALIVE: case CHOKE: case UNCHOKE: case INTERESTED: case NOTINTERESTED:
	        return type.messageHead;
	     
	     case HAVE:
	        outMessage = new byte[4 + 5];
	        System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
	        System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
	        return outMessage;
	     
	     case REQUEST: case CANCEL:
	        outMessage = new byte[4 + 13];
	        System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
	        System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
	        return outMessage;

	     case BITFIELD:
	        outMessage = new byte[(4 + 1) + messageTail.length];
	        System.arraycopy(type.messageHead, 0, outMessage, 0, 5);
	        System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
	        return outMessage;

	     case PIECE:
	        outMessage = new byte[(4 + 1) + messageTail.length];
	        byte[] lengthPrefix = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt( 1 + messageTail.length).array();
	        System.arraycopy(lengthPrefix, 0, outMessage, 0, 4);
	        System.arraycopy(type.messageHead, 4, outMessage, 4, 1);
	        System.arraycopy(messageTail, 0, outMessage, 5, messageTail.length);
	        return outMessage;
	     default:
	        throw new RuntimeException("Type not recognized: " + type.toString());
	  }
	}

	public static byte[] buildHaveTail(int index){
	  return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
	}

	public static byte[] buildRCTail(int index, int begin, int length){
		byte[] messageTail = new byte[12];
		byte[] indexArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
		byte[] beginArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(begin).array();
		byte[] lengthArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array();
		System.arraycopy(indexArray, 0, messageTail, 0, 4);
		System.arraycopy(beginArray, 0, messageTail, 4, 4);
		System.arraycopy(lengthArray, 0, messageTail, 8, 4);
		return messageTail;
	}

	public static byte[] buildPieceTail(int index, int begin, byte[] block){
	  byte[] messageTail = new byte[8 + block.length];
	  byte[] indexArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(index).array();
	  byte[] beginArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(begin).array();
	  System.arraycopy(indexArray, 0, messageTail, 0, 4);  
     System.arraycopy(beginArray, 0, messageTail, 4, 4);
	  System.arraycopy(block, 0, messageTail, 8, block.length);
	  return messageTail;
	}
}

class MessageData{
	public final byte[] message;
	public Message type;

	public int msgLength;
	public byte id;
	public int pieceIndex;
	public byte[] bitfield;
	public int beginIndex;
	public int blckLength;
	public byte[] block;
   
  
   //Cascading intentional for switches

   MessageData(Message i_type) throws IllegalArgumentException{
      switch(i_type){
         
         case CHOKE: case UNCHOKE: case INTERESTED: case NOTINTERESTED:
            msgLength = 1;
            id = i_type.getMessageHead()[4];
         case KEEPALIVE:
            type = i_type;
            message = Message.encodeMessage(type);
            break;
         
         case HAVE: case BITFIELD: case REQUEST: case PIECE: case CANCEL:
            throw new IllegalArgumentException("Wrong contructor used for Message type: " + i_type);
         default:
            throw new IllegalArgumentException("Unknown type: " + i_type);
      }
   } 

   MessageData(Message i_type, int i_index){
      switch (i_type){
         case HAVE:
            msgLength = 5;
            id = 4;
            pieceIndex = i_index;
            type = i_type;
            message = Message.encodeMessage(type, Message.buildHaveTail(pieceIndex));
            break;
         
         case KEEPALIVE: case BITFIELD: case REQUEST: case PIECE: case CANCEL:
         case CHOKE: case UNCHOKE: case INTERESTED: case NOTINTERESTED:
            throw new IllegalArgumentException("Wrong constructor used for type: " + i_type);
         default:
            throw new IllegalArgumentException("Unknown type: " + i_type);
      }
   }     

   MessageData(Message i_type, byte[] i_bitfield){
      switch (i_type){
         case BITFIELD:
            msgLength = 1 + i_bitfield.length;
            id = 5;
            type = i_type;
            bitfield = i_bitfield;
            message = Message.encodeMessage(type, bitfield);
            break;
         
         case KEEPALIVE: case HAVE: case REQUEST: case PIECE: case CANCEL:
         case CHOKE: case UNCHOKE: case INTERESTED: case NOTINTERESTED:
            throw new IllegalArgumentException("Wrong constructor used for type: " + i_type);
         default:
            throw new IllegalArgumentException("Unknown type: " + i_type);
      }
   }

   MessageData(Message i_type, int i_index, int i_begin, int i_length){
      switch (i_type){
         case REQUEST:
         case CANCEL:
            type = i_type;
            msgLength = 13;
            id = i_type.getMessageHead()[4];
            type = i_type;
            pieceIndex = i_index;
            beginIndex = i_begin;
            blckLength = i_length;
            message = Message.encodeMessage(type, Message.buildRCTail(pieceIndex, beginIndex, blckLength));
            break;
         
         case KEEPALIVE: case HAVE: case CHOKE: case UNCHOKE: case BITFIELD:
         case INTERESTED: case NOTINTERESTED: case PIECE:
            throw new IllegalArgumentException("Wrong constructor used for type: " + i_type);
         default:
            throw new IllegalArgumentException("Unknown type: " + i_type);
      }
   }

   MessageData(Message i_type, int i_index, int i_begin, byte[] i_block){
      switch (i_type){
         case PIECE:
            type = i_type;
            msgLength = 9 + i_block.length;
            id = 7;
            pieceIndex = i_index;
            beginIndex = i_begin;
            block = i_block;
            message = Message.encodeMessage(type, Message.buildPieceTail(pieceIndex, beginIndex, block));
            break;

         case KEEPALIVE: case HAVE: case CHOKE: case UNCHOKE: case BITFIELD:
         case INTERESTED: case NOTINTERESTED: case REQUEST: case CANCEL: 
            throw new IllegalArgumentException("Wrong constructor used for type: " + i_type);
         default:
            throw new IllegalArgumentException("Unknown type: " + i_type);
      }
   }

	MessageData(byte[] array) {
		message = array;
		type = Message.getType(array);
		msgLength = ByteBuffer.wrap(array).getInt();

		switch (type){
			case KEEPALIVE:
				break;
			case BITFIELD:
				bitfield = new byte[msgLength - 1];
				System.arraycopy(array, 5, bitfield, 0, msgLength -1 );
				break;
			case REQUEST: case CANCEL:
				blckLength = ByteBuffer.wrap(array, 13, 4).getInt();
				pieceIndex = ByteBuffer.wrap(array, 5, 4).getInt();
				beginIndex = ByteBuffer.wrap(array, 9, 4).getInt();
				break;
			case PIECE:
				block = new byte[msgLength - 9];
				System.arraycopy(array, 13, block, 0, msgLength - 9); 
				pieceIndex = ByteBuffer.wrap(array, 5, 4).getInt();
				beginIndex = ByteBuffer.wrap(array, 9, 4).getInt();
				break;
			case HAVE:
				pieceIndex = ByteBuffer.wrap(array, 5, 4).getInt();
			   break;
			default:
				this.id = this.message[4];
				break;
		}
	}     
}
