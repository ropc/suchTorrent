import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.File;
import static java.nio.file.StandardOpenOption.*;
public class Writer{
	private int pSize;//The piece size for the current torrent. Used by the message method.
    FileChannel fileWriter;
	
    public Writer(String Filename, int pieceSize){
		pSize = pieceSize;//Initialize the piece size
		Path file = Paths.get("");//Get the current directory's position.
		file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),Filename);//Get the absolute path of the present directory, and append the desired filename
		try{	
			File x = new File(file.toAbsolutePath().toString());//Create a File object with the path that we created above
			x.createNewFile();									//create the new file with the specified name in the present directory [You can also say /fakefolder/filename to put it in a subdirectory of the present folder]
			fileWriter = FileChannel.open(file,READ,WRITE);		//Create a file writer for this new file
		}catch(Exception e){
		System.err.println("Error Opening File. Please try a different filename. Valid Input : program [torrent file] [output file]");
		//e.printStackTrace();
		}
    }
   
    public void WriteData(int position, Byte data){//Write one byte at the specified location in the file. Will expand the file if necessary
        try{
			byte[] x = new byte[1];
			x[0]=data; //Catch the byte, and wrap it up to be written by the filewriter
			ByteBuffer s = ByteBuffer.wrap(x);
			
        	long pos = Long.valueOf(position);//Don't you just love java datatype conversion?
			fileWriter.write(s,pos);//Write the byte to the specified position
		}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }

    public void WriteData(int position, ByteBuffer writeall){//Write all of the buffer to the specified location in the file. Will expand the file if necessary
        try{
			long pos = Long.valueOf(position);//Same as above but the data is already formatted for the FileChannel
        	fileWriter.write(writeall,pos);
		}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
	
   //Write a subset of the buffer beginning @ offset and ending at offset+numbertoprint to the file. Will expand the file if necessary.
    public void WriteData(int position, int offset, int numberToPrint, ByteBuffer writeFromHere){
        long pos = Long.valueOf(position);
		try{
			byte[] x = new byte[numberToPrint];//Create a byte array to hold the subset of the bytebuffer you want to read from
			
			int i;
			for(i=0;i<numberToPrint;i++){x[i]=(writeFromHere.get(i+offset));}//Walk across the bytebuffer for the specified length and copy to x
			
			ByteBuffer s = ByteBuffer.wrap(x);//wrap x up and send it to the fileWriter.
			fileWriter.write(s,pos);
		}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
	
    //Write a given message to the file. Has already been checked for validity. Only usable when we get a message=piecesize
    public void WriteMessage(byte[] array){
		int msgLength = ByteBuffer.wrap(array).getInt();		//Get the length of the data to be written.
		byte[] block = new byte[msgLength-9];					//Create a byte array to hold that data.
		System.arraycopy(array, 13, block, 0, msgLength-9);		//copy the data into this block.
		int pieceIndex = ByteBuffer.wrap(array,5,4).getInt();	//Get the piece number that this data is from.
		int beginIndex = ByteBuffer.wrap(array,9,4).getInt();	//Get the offset within that piece.
		int position = ((pSize*pieceIndex) + beginIndex);		//Calculate the final position in the file from the above data
		
		WriteData(position,block);								//Write the data to the file
    }

   


}
