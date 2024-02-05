import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class HexParser {
    private final ArrayList<HexFileLine> linesDecoded = new ArrayList<>();

    public HexParser() {

    }

    public void parseFile(File file) throws FileNotFoundException, HexLineNotCorrectException, WrongCheckSumException {
        System.out.println("Trying to open file: " + file);
        Scanner myReader = new Scanner(file);
        int i = 0;
        while (myReader.hasNextLine()) {
            String data = myReader.nextLine();
            linesDecoded.add(new HexFileLine(data));
        }
    }

    public void printDecodedLines() {
        linesDecoded.forEach(System.out::println);
    }
}
