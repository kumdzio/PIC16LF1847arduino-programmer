import java.util.ArrayList;

public class HexLine {
    private String size;
    private String address;
    private String type;
    private String dataAndChecksum;

    public HexLine(String line) {
        size = line.substring(1, 3);
        address = line.substring(3, 7);
        type = line.substring(7, 9);
        dataAndChecksum = line.substring(9);
    }

    public String getType() {
        return type;
    }

    public String getSize() {
        return size;
    }

    public String getAddress() {
        return address;
    }

    public String getDataAndChecksum() {
        return dataAndChecksum;
    }

    @Override
    public String toString() {
        return size + address + type + dataAndChecksum;
    }

    public byte[] parseForSend() {
        String fullLine = toString();

        ArrayList<Byte> temporaryList = new ArrayList<>();
        temporaryList.add((byte) 'p');
        for (int i = 0; i < fullLine.length(); i += 2) {
            temporaryList.add(parseByte(fullLine.substring(i, i + 2)));
        }
        byte[] result = new byte[temporaryList.size()];
        for (int i = 0; i < temporaryList.size(); i++) {
            result[i] = temporaryList.get(i);
        }
        return result;
    }

    byte parseByte(String s){
        return (byte) (parseHexChar(s.charAt(0))<<4|parseHexChar(s.charAt(1)));
    }

    byte parseHexChar(char c){
        return switch (c) {
            case '0' -> 0b0;
            case '1' -> 0b1;
            case '2' -> 0b10;
            case '3' -> 0b11;
            case '4' -> 0b100;
            case '5' -> 0b101;
            case '6' -> 0b110;
            case '7' -> 0b111;
            case '8' -> 0b1000;
            case '9' -> 0b1001;
            case 'A', 'a' -> 0b1010;
            case 'B', 'b' -> 0b1011;
            case 'C', 'c' -> 0b1100;
            case 'D', 'd' -> 0b1101;
            case 'E', 'e' -> 0b1110;
            case 'F', 'f' -> 0b1111;
            default -> throw new IllegalArgumentException();
        };
    }
}
