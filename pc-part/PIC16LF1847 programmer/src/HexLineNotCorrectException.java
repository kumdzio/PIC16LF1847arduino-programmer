public class HexLineNotCorrectException extends Exception{
    public String getLine() {
        return line;
    }

    String line;
    public HexLineNotCorrectException(String line){
        this.line = line;
    }
}
