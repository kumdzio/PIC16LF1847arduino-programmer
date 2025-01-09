//IMPORTANT: Use a 3.3V Arduino board!
//If the built-in LED goes dark, the fix was applied successfully.

//PIC 16LF1847 commands
#define LOAD_CONFIGURATION 0b000000
#define LOAD_DATA_FOR_PROGRAM_MEMORY 0b000010
#define LOAD_DATA_FOR_DATA_MEMORY 0b000011
#define READ_DATA_FROM_PROGRAM_MEMORY 0b000100
#define READ_DATA_FROM_DATA_MEMORY 0b000101
#define INCREMENT_ADDRESS 0b000110
#define RESET_ADDRESS 0b010110
#define BEGIN_INTERNALLY_TIMED_PROG 0b001000
#define BEGIN_EXTERNALLY_TIMED_PROG 0b011000
#define END_EXTERNALLY_TIMED_PROG 0b001010
#define BULK_ERASE_PROGRAM_MEMORY 0b001001
#define BULK_ERASE_DATA_MEMORY 0b001011
#define ROW_ERASE_PROGRAM_MEMORY 0b010001

//Pinout
#define CLK D2
#define DATA D3

unsigned int current_address;

bool verify(byte lsb, byte msb);
void blink_error(int number);
//High level PIC functions
int read_id();
bool load_data_for_data_memory(byte size, int address, byte *data);
void data_program_cycle(byte lsb, byte msb);
void go_to_address(int address);
bool read_and_verify_id();
bool check_known_eeprom();
//Low level PIC functions
void go_to_configuration_address();
void write_two_bytes(byte lsb, byte msb);
void clk_pulse();
void increment_address();
void reset_address();
void send_command(byte command);
int read_program();
int read_data();

//##########################################################
//##########            IMPLEMENTATION            ##########
//##########################################################

//#################
//ARDUINO FUNCTIONS
//#################
void setup() {
  Serial.begin(115200);
  pinMode(CLK, OUTPUT);
  pinMode(DATA, OUTPUT);
  digitalWrite(DATA, LOW);
  digitalWrite(CLK, LOW);
  pinMode(LED_BUILTIN, OUTPUT);
  current_address = 0;
    Serial.println(F("Dyson Battery original firmware unlocker by Kumdzio"));
  
  while (true) {
    Serial.println(F("Checking if PIC16LF1847 is detected."));
    digitalWrite(LED_BUILTIN, HIGH);
    if (read_and_verify_id()) {
      reset_address();
      if (!check_known_eeprom()) {
        Serial.println(F("Beggining of EEPROM is different than expected."));
        blink_error(10);
        return;
      } else {
        Serial.println(F("Beggining of EEPROM is as expected."));
      }
      Serial.println(F("Attempting fix."));
      byte data[4];
      data[0] = 0;
      data[1] = 0;
      data[2] = 0x7F;
      data[3] = 0;
      if (load_data_for_data_memory(4, 0x1E, data)) {
        Serial.println(F("Fix applied successfully."));
        return;
      } else {
        Serial.println(F("Verification of written fix failed."));
        blink_error(5);
      }
      reset_address();
    }
    digitalWrite(LED_BUILTIN, LOW);
    delay(1000);
  }
}

void loop() {
  delay(1000);
}

void blink_error(int number) {
  for (int i = 0; i < number; i++) {
    digitalWrite(LED_BUILTIN, LOW);
    delay(300);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(300);
  }
}

bool check_known_eeprom(){
  int expected[6] = {0x50, 0x53, 0x31, 0x20, 0x56, 0x31};
  for(int i=0;i<6;i++){
    if(expected[i]!=read_data()){
      return false;
    }
    increment_address();
  }
  return true;
}

bool read_and_verify_id() {
  int id = read_id();
  if ((id & 0x3FE0) == 0x14A0) {
    Serial.println("Detected PIC18LF1847!");
    return true;
  } else if (id == 0x3FFF) {
    Serial.println("Please connect 8-9V VPP now");
  } else {
    Serial.print("Read ID is not correct. Should be 0x14A0 and is: 0x");
    Serial.println(id, HEX);
  }
  return false;
}

bool verify(byte lsb, byte msb, int read) {
  return read == (((msb & 0X3f) << 8) | lsb);
}


int read_id() {
  go_to_configuration_address();
  go_to_address(0x8006);
  int data = read_program();
  return data;
}

bool load_data_for_data_memory(byte size, int address, byte *data) {
  go_to_address(address);
  for (byte i = 0; i < size; i += 2) {
    data_program_cycle(data[i], data[i + 1]);
    if (verify(data[i], data[i + 1], read_data())) {
      increment_address();
    } else {
      return false;
    }
  }
  return true;
}

void data_program_cycle(byte lsb, byte msb) {
  send_command(LOAD_DATA_FOR_DATA_MEMORY);
  write_two_bytes(lsb, msb);
  send_command(BEGIN_INTERNALLY_TIMED_PROG);
  delay(10);
}

void go_to_address(int address) {
  while (current_address < address) {
    increment_address();
  }
}

//#######################
//LOW LEVEL PIC FUNCTIONS
//#######################

void go_to_configuration_address() {

  digitalWrite(CLK, LOW);
  digitalWrite(DATA, LOW);
  delay(1);
  send_command(LOAD_CONFIGURATION);
  //empty data which goes into latch
  for (int i = 0; i < 16; i++) {
    clk_pulse();
  }
  current_address = 0x8000;
}

void write_two_bytes(byte lsb, byte msb) {
  pinMode(DATA, OUTPUT);
  digitalWrite(CLK, LOW);
  digitalWrite(DATA, LOW);
  clk_pulse();
  for (int i = 0; i < 8; i++) {
    digitalWrite(DATA, lsb >> i & 1);
    clk_pulse();
  }
  for (int i = 0; i < 6; i++) {
    digitalWrite(DATA, msb >> i & 1);
    clk_pulse();
  }
  digitalWrite(DATA, LOW);
  clk_pulse();
  delay(1);
}

void clk_pulse() {
  digitalWrite(CLK, HIGH);
  digitalWrite(CLK, LOW);
}

void increment_address() {
  send_command(INCREMENT_ADDRESS);
  if (current_address == 0x7FFF) {
    current_address = 0;
  } else if (current_address == 0xFFFF) {
    current_address = 0x8000;
  } else {
    current_address++;
  }
}

void reset_address() {
  send_command(RESET_ADDRESS);
  current_address = 0;
}

void send_command(byte command) {
  pinMode(DATA, OUTPUT);
  digitalWrite(CLK, LOW);
  digitalWrite(DATA, LOW);
  for (int i = 0; i < 6; i++) {
    digitalWrite(DATA, command >> i & 1);
    clk_pulse();
  }
  delay(1);
}

int read_program() {
  int result = 0;
  send_command(READ_DATA_FROM_PROGRAM_MEMORY);
  pinMode(DATA, INPUT);
  clk_pulse();
  clk_pulse();
  for (int i = 0; i < 14; i++) {
    result |= digitalRead(DATA) << i;
    clk_pulse();
  }
  return result;
}

int read_data() {
  int result = 0;
  send_command(READ_DATA_FROM_DATA_MEMORY);
  pinMode(DATA, INPUT);
  clk_pulse();
  clk_pulse();
  for (int i = 0; i < 14; i++) {
    result |= digitalRead(DATA) << i;
    clk_pulse();
  }
  return result;
}
