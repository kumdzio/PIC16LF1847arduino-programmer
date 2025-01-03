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
            temporaryList.add(Byte.parseByte(fullLine.substring(i, i + 2), 16));
        }
        byte[] result = new byte[temporaryList.size()];
        for (int i = 0; i < temporaryList.size(); i++) {
            result[i] = temporaryList.get(i);
        }
        return result;
    }
}
