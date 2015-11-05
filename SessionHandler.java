/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import static java.nio.file.StandardOpenOption.*;
import java.util.concurrent.locks.*;

public class SessionHandler{
    FileChannel infoFileWriter;
    File Info,Actual;
    ReentrantLock lock;
    int pieceSize;
    /**
    *Creates a SessionHandler object with a corresponding filename and piecesize
    *
    *@param filename = the name you want the file to be
    *@param pSize = Size of a Piece in this torrent
    */
    public SessionHandler(String fileName, int pSize) throws IOException {
		lock = new ReentrantLock();
		pieceSize=pSize;
        	Path file = Paths.get("");//Get the current directory's position.
		Path f2 = Paths.get("");
		f2 = FileSystems.getDefault().getPath(f2.toAbsolutePath().toString(),fileName);
        	file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),fileName+".info");//Get the absolute path of the present directory, and append the desired filename
        try{   
            Info = new File(file.toAbsolutePath().toString());
			Actual = new File(f2.toAbsolutePath().toString());
            if(!Info.exists()){
                Info.createNewFile();//create the new file with the specified name in the present directory [You can also say /fakefolder/filename to put it in a subdirectory of the present folder]
            }
            infoFileWriter = FileChannel.open(file,READ,WRITE);//Create a file writer for this new file
        } catch (Exception e) {
            System.err.println("Error Opening File. Please try a different filename. Valid Input : program [torrent file] [output file]");
            throw e;
        }
		
		
    }
   
    /**
    *Loads the session from the file into memory and returns it
    *
    *return:the byte array representing the parts of the file already written as a byte array
    */
    public byte[] loadSession() throws IOException{
        lock.lock();
	ByteBuffer result = ByteBuffer.allocate((int)infoFileWriter.size());
        infoFileWriter.read(result);
	lock.unlock();
        return result.array();
    }
   
    /**
    *As above, but returns as a ByteBuffer in case that is better.
    */
    public ByteBuffer loadSessionBuff() throws IOException{
        lock.lock();
	ByteBuffer result = ByteBuffer.allocate((int)infoFileWriter.size());
        infoFileWriter.read(result);
	lock.unlock();
        return result;
    }
   
    /**
    *Writes the session for this torrent out to a file
    *@param f - The byte array representing the data we have thusfar
    *returns: true if the file was successfully written, false (or error thrown)otherwise
    **/
    public boolean writeSession(byte[] f) throws IOException {
        boolean ret = false;
        lock.lock();
	ret = Info.delete(); //The only way to replace data is to delete then rewrite in Java, so here we go
        if(ret){
            Info.createNewFile();//Remake the file in its original image
        }
        try{
            infoFileWriter = FileChannel.open(FileSystems.getDefault().getPath(Info.getAbsolutePath(),""),READ,WRITE);//Create a file writer for this file
            ByteBuffer s = ByteBuffer.wrap(f);
            infoFileWriter.write(s,0);//Write the current session out to the session file
        }catch(Exception e){
            System.err.println("Error writing session!");
            throw e;
        }
        lock.unlock();
        return Info.exists()&&ret;
    }

	/**
	*This method reads all of the previously downloaded info into memory so it can be sent out when requested
	*Probably shouldn't be used but here we go anyway.
	*
	*@param pieceSize - Need the size of the pieces so this can read in correct sized blocks
	*returns: a byte[][] that contains messages that prepackage the pieces already downloaded in a prev session
	**/
    public byte[][] getPrevSessionMessages() throws IOException{
        byte[][] result;
        int numOn=0;
        int size = (int)infoFileWriter.size();
        ByteBuffer s = ByteBuffer.allocate(size);
        infoFileWriter.read(s);
        byte[] x = new byte[size];
        x=s.array();
       
        for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                    //Byte i, offset j is on! (Have to do this so I know how many arrays to allocate)
                    numOn++;
                }
               
            }
        }
       
	x=s.array();
        result = new byte[numOn][13+pieceSize];
        int test=0;
        byte[] data = new byte[pieceSize];
	byte[] tail = new byte[pieceSize+8];
	RandomAccessFile f = new RandomAccessFile(Actual,"r");
	Message m = Message.PIECE;
        
	for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                    //Byte i, offset j is on!
                    //read(8i+jth piece from file);
                    int pos = pieceSize*((8*i)+j);//THIS IS WHERE YOU WOULD CHANGE IF YOU MESSED UP BIG ENDIAN STUFF
					f.read(data,pos,pieceSize);
					tail = m.buildPieceTail(pos,0,data);
					result[test] = m.encodeMessage(Message.PIECE,tail);
                    test++;
                }
               
            }
        }
       
		return result;
    }
   
	/**
	* This method loads up the data from the previous session into a byte array that holds piece data
	*
	* @param pieceSize The size of a piece for this file.
	* returns a byte[][] populated with all of the data downloaded so far. 
	*/   
	public byte[][] getPrevSessionData() throws IOException{
		lock.lock();
		int size = (int)infoFileWriter.size();
		byte[] x = new byte[size];
		ByteBuffer s = ByteBuffer.allocate(size);
		infoFileWriter.read(s);
		lock.unlock();
		byte[][] result = new byte[(8*size)][pieceSize]; //Size is # of bytes in bitfield file, *8 to get # of bits
		//Was going to look for the last bit that is on, so I can stop my read, but I realize that 
		//reads only occur when there is a 1, so walking past it won't be a problem as it will never read
	    	x=s.array();
		RandomAccessFile f = new RandomAccessFile(Actual,"r");
		for(int i=0; i<size;i++){
            for(int j=0;j<8;j++){
                if(((x[i]>>j)&1)==1){
                    	//Byte i, offset j is on!
                    	//read(8i+jth piece from file);
                	int pos = pieceSize*((8*i)+j);//THIS IS WHERE YOU WOULD CHANGE IF YOU MESSED UP BIG ENDIAN STUFF
			f.read(result[(8*i)+j],pos,pieceSize);
                }
               
            }
        }
		return result;
	}
   
   /**
   * This method gets a piece of the file to send out to a peer
   * 
   * @param pieceIndex - The index of the piece to be returned
   * @offsetWithinPiece - The <begin> offset specified by the peer
   * @sizeOfRequest - The size of the request being made
   * returns the data specified by the above.
   */
   public byte[] getPiece(int pieceIndex, int offsetWithinPiece, int sizeOfRequest) throws IOException{
	   RandomAccessFile f = new RandomAccessFile(Actual,"r");
	   byte[] ret = new byte[sizeOfRequest];
	   int position = (pieceSize*pieceIndex)+offsetWithinPiece;
	   f.read(ret,position,sizeOfRequest);
	   return ret;
   }
   
   
   /**
   * This method gets a piece of the file to send out to a peer, and packages it as a message
   * 
   * @param pieceIndex - The index of the piece to be returned
   * @offsetWithinPiece - The <begin> offset specified by the peer
   * @sizeOfRequest - The size of the request being made
   * returns the message containing the data specified by the above.
   */
   public byte[] getPieceMessage(int pieceIndex, int offsetWithinPiece, int sizeOfRequest) throws IOException{
	   byte[] data = new byte[sizeOfRequest];
	   byte[] message = new byte[13+sizeOfRequest];
	   Message m = Message.PIECE;
	   data = getPiece(pieceIndex, offsetWithinPiece, sizeOfRequest);
	   message = m.encodeMessage(Message.PIECE,m.buildPieceTail(pieceIndex,offsetWithinPiece,data));
	   return message;
   }
   
   
   
}
