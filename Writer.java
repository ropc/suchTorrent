import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.File;
import static java.nio.file.StandardOpenOption.*;
public class Writer{
	private int pSize;
    FileChannel fileWriter;
    public Writer(String Filename, int pieceSize){
	pSize = pieceSize;
	Path file = Paths.get("");//Get the current directory's position.
	file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),Filename);//Get the absolute path of the present directory, and append the desired filename
	try{	
	File x = new File(file.toAbsolutePath().toString());
	x.createNewFile();//create the new file with the specified name in the present directory [You can also say /fakefolder/filename to put it in a subdirectory of the present folder]
	fileWriter = FileChannel.open(file,READ,WRITE);//Create a file writer for this new file
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
	long pos = Long.valueOf(position);
        fileWriter.write(writeall,pos);
	}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
   
    public void WriteData(int position, int offset, int numberToPrint, ByteBuffer writeFromHere){//Write a subset of the buffer beginning @ offset and ending at offset+numbertoprint to the file. Will expand the file if necessary.
        long pos = Long.valueOf(position);
	try{
	byte[] x = new byte[numberToPrint];
	int i;
	for(i=0;i<numberToPrint;i++){x[i]=(writeFromHere.get(i+offset));}
	ByteBuffer s = ByteBuffer.wrap(x);
	fileWriter.write(s,pos);
	}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
	
    //Write a given message to the file. Has already been checked for validity. Only usable when we get a message=piecesize
    public void WriteMessage(byte[] array){
	int msgLength = ByteBuffer.wrap(array).getInt();
	byte[] block = new byte[msgLength-9];
	System.arraycopy(array, 13, block, 0, msgLength-9);
	int pieceIndex = ByteBuffer.wrap(array,5,4).getInt();
	int beginIndex = ByteBuffer.wrap(array,9,4).getInt();
	int position = ((pSize*pieceIndex) + beginIndex);
	WriteData(position,block);
    }

   


}
