/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.nio.*; 
import java.util.*;

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
