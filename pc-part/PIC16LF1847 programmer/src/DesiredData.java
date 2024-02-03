import java.util.ArrayList;

public class DesiredData {
    private static final int MAX_PROGRAM_MEMORY_SIZE=1;
    private static final int MAX_EEPROM_SIZE=1;
    private static final int MAX_CONFIGURATION_WORDS_SIZE=1;


    short[] programMemory = new short[MAX_PROGRAM_MEMORY_SIZE];
    short[] eeprom = new short[MAX_EEPROM_SIZE];
    short[] configurationWords = new short[MAX_CONFIGURATION_WORDS_SIZE];

    public DesiredData(){

    }

    public short[] getProgramMemory() {
        return programMemory;
    }

    public short[] getEeprom() {
        return eeprom;
    }

    public short[] getConfigurationWords() {
        return configurationWords;
    }

}
