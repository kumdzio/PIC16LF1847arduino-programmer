public class WrongCheckSumException extends Exception{
    private final String line;
    private final byte calculated;
    private final byte saved;

    public WrongCheckSumException(String line,byte calculated, byte saved){
        this.line = line;
        this.calculated = calculated;
        this.saved = saved;
    }

    public String getLine() {
        return line;
    }

    public byte getCalculated() {
        return calculated;
    }

    public byte getSaved() {
        return saved;
    }
}
