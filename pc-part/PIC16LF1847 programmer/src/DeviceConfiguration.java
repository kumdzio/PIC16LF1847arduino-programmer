public class DeviceConfiguration {
    public String UserID1="";
    public String UserID2="";
    public String UserID3="";
    public String UserID4="";
    public String DeviceID="";
    public String Configuration1="";
    public String Configuration2="";

    public boolean isComplete(){
        return !UserID1.isBlank()
                && !UserID2.isBlank()
                && !UserID3.isBlank()
                && !UserID4.isBlank()
                && !DeviceID.isBlank()
                && !Configuration1.isBlank()
                && !Configuration2.isBlank();
    }
}
