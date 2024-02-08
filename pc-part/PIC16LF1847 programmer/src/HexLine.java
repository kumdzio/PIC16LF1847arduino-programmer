public class HexLine {
    private String size;
    private String address;
    private String type;
    private String rest;
    public HexLine(String line){
        size= line.substring(1,3);
        address = line.substring(3,7);
        type = line.substring(7,9);
        rest = line.substring(9);
    }

    public String getType(){
        return type;
    }
    public String getSize() {
        return size;
    }

    public String getAddress() {
        return address;
    }

    public String getRest() {
        return rest;
    }

    @Override
    public String toString() {
        return size+address+rest;
    }
}
