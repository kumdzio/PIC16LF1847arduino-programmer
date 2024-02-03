import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class HexParser {
    public HexParser(){

    }
    public DesiredData parseFile(File file){
        System.out.println("Trying to open file: "+file);
        try {
            Scanner myReader = new Scanner(file);
            int i=0;
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                System.out.println("Line "+ i++ + ": " +data);
            }
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }
}
