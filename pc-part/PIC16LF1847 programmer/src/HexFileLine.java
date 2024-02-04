import javax.swing.*;
import java.util.Arrays;

public class HexFileLine {
    private byte size = 0;
    private short address = 0;
    private byte type = -1;
    private final short[] data;
    private byte checksum = 0;
    private final String originalLine;
    public HexFileLine(String line) throws HexLineNotCorrectException {
        originalLine = line;
        if(line.getBytes()[0]!=':'){
            throw new HexLineNotCorrectException(line);
        }
        try{
            size = convertToByte(line.substring(1,3));
            address = (short) ((convertToByte(line.substring(3,5))<<8) + convertToByte(line.substring(5,7)));
            type = convertToByte(line.substring(7,9));
            data = new short[size/2];
            for(int i=0;i<size/2;i++){
                data[i] = (short) (convertToByte(line.substring(9+i*4,11+i*4)) + (convertToByte(line.substring(11+i*4,13+i*4))<<8));
            }
            checksum = convertToByte(line.substring(line.length()-2));
        } catch (NonHexCharException e) {
            throw new HexLineNotCorrectException(line);
        }

    }
    private byte convertToByte(String twoHexDigits) throws NonHexCharException {
        return (byte) ((convertChar(twoHexDigits.charAt(0))<<4)+convertChar(twoHexDigits.charAt(1)));
    }

    private byte convertChar(char c) throws NonHexCharException {
        return switch (c) {
            case '0' -> 0;
            case '1' -> 1;
            case '2' -> 2;
            case '3' -> 3;
            case '4' -> 4;
            case '5' -> 5;
            case '6' -> 6;
            case '7' -> 7;
            case '8' -> 8;
            case '9' -> 9;
            case 'A', 'a' -> 10;
            case 'B', 'b' -> 11;
            case 'C', 'c' -> 12;
            case 'D', 'd' -> 13;
            case 'E', 'e' -> 14;
            case 'F', 'f' -> 15;
            default -> throw new NonHexCharException();
        };

    }

    @Override
    public String toString() {
        return "HexFileLine{" +
                "size=" + size +
                ", address=" + address +
                ", type=" + type +
                ", data=" + Arrays.toString(data) +
                ", checksum=" + checksum +
                ", originalLine='" + originalLine + '\'' +
                '}';
    }
}
