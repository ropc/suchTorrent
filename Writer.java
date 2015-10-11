import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.File;
import static java.nio.file.StandardOpenOption.*;
public class Writer{
    FileChannel fileWriter;
    public Writer(String Filename){
	Path file = Paths.get("");
	file = FileSystems.getDefault().getPath(file.toAbsolutePath().toString(),Filename);
        //System.err.println(file.toAbsolutePath().toString());
	//file = FileSystems.getDefault().getPath(Filename);
	try{	
	File x = new File(file.toAbsolutePath().toString());
	x.createNewFile();
	fileWriter = FileChannel.open(file,READ,WRITE);
	}catch(Exception e){System.err.println("ERROR OPENING FILE:");e.printStackTrace();}
    }
   
    public void WriteData(int position, Byte data){
        try{
	byte[] x = new byte[1];
	x[0]=data;
	ByteBuffer s = ByteBuffer.wrap(x);        
        long pos = Long.valueOf(position);
        fileWriter.write(s,pos);
	}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }

    public void WriteData(int position, ByteBuffer writeall){
        try{
	long pos = Long.valueOf(position);
        fileWriter.write(writeall,pos);
	}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
   
    public void WriteData(int position, int offset, int numberToPrint, ByteBuffer writeFromHere){
        long pos = Long.valueOf(position);
	try{
	byte[] x = new byte[numberToPrint];
	int i;
	for(i=0;i<numberToPrint;i++){x[i]=(writeFromHere.get(i+offset));}
	ByteBuffer s = ByteBuffer.wrap(x);
	fileWriter.write(s,pos);
	}catch(Exception e){System.err.println("ERROR WRITING TO FILE");}
    }
   
   


}
