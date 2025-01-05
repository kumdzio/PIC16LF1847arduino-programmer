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

//Intel HEX commands types
#define HEX_TYPE_DATA 0x00
#define HEX_TYPE_END_OF_FILE 0x01
#define HEX_TYPE_EXTENDED_SEGMENT_ADDRESS 0x02
#define HEX_TYPE_START_SEGMENT_ADDRESS 0x03
#define HEX_TYPE_EXTENDED_LINEAR_ADDRESS 0x04
#define HEX_TYPE_START_LINEAR_ADDRESS 0x05

//Pinout
#define CLK 2
#define DATA 3

//error codes
#define ERROR_CODE_NONE 0
#define ERROR_CODE_TEST 1
#define ERROR_CODE_02_HEX_NOT_SUPPORTED 2
#define ERROR_CODE_03_HEX_NOT_SUPPORTED 3
#define ERROR_CODE_05_HEX_NOT_SUPPORTED 4
#define ERROR_CODE_UNRECOGNIZED_HEX_TYPE 5
#define ERROR_CODE_WRONG_SIZE_OF_DATA 6
#define ERROR_CODE_WRONG_DATA 7
#define ERROR_CODE_UNSUPPORTED_ADDRESS 8
#define ERROR_CODE_LOAD_CONFIGURATION_VERIFICATION_FAILED 9
#define ERROR_CODE_LOAD_DATA_VERIFICATION_FAILED 10
#define ERROR_CODE_LOAD_PROGRAM_VERIFICATION_FAILED 11

#define DEBUG 0
#define SIMULATE_SUCCESS false

unsigned int current_address;
bool extended_linear_address;

//structural functions
void programming();
bool verify(byte lsb, byte msb);
//Computer communication functions
void waitForSerialData(int requestedBytesNumber);
void handle_error(int code);
void debug(String message);
void debugnlf(String message);//No Line Feed
//High level PIC functions
void read_id();
bool load_data_for_data_memory(byte size, int address, byte *data);
bool load_data_for_program_memory(byte size, int address, byte *data);
void one_word_program_cycle(byte lsb, byte msb);
void data_program_cycle(byte lsb, byte msb);
void go_to_address(int address);
//Low level PIC functions
void go_to_configuration_address();
void write_two_bytes(byte lsb, byte msb);
void erase_chip();
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
  Serial.begin(57600);
  pinMode(CLK, OUTPUT);
  pinMode(DATA, OUTPUT);
  digitalWrite(DATA, LOW);
  digitalWrite(CLK, LOW);
  pinMode(LED_BUILTIN, OUTPUT);
  current_address = 0;
  extended_linear_address = 0;
}

void loop() {
  byte command;
  if (Serial.available()) {
    command = Serial.read();
    switch (command) {
      case 'h':
        digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));
        Serial.print(F("Arduino PIC16(L)F1847 programmer by Kumdzio"));
        break;
      case 'i':
        Serial.println(F("Reading ID of device:"));
        read_id();
        break;
      case 'p':
        debug(F("Received programming command."));
        programming();
        break;
      case 'r':
        debug(F("Erasing chip."));
        erase_chip();
        Serial.print('k');
        break;
    }
  }
}

//####################
//STRUCTURAL FUNCTIONS
//####################
void programming() {
  debug(F("Provide size of command:"));
  waitForSerialData(1);
  byte size = Serial.read();
  debugnlf(F("Received size: "));
  debug(String(size));

  debug(F("Provide address:"));
  waitForSerialData(2);
  unsigned int address = Serial.read() << 8 | Serial.read();
  address/=2;
  debugnlf(F("Received address: "));
  debug(String(address));

  debug(F("Provide type of command:"));
  waitForSerialData(1);
  byte type = Serial.read();
  debugnlf(F("Received type of command: "));
  debug(String(type));

  debugnlf(F("Provide "));
  debugnlf(String(size));
  debug(F(" bytes of data now:"));
  byte data[size];
  for (byte i = 0; i < size; i++) {
    waitForSerialData(1);
    data[i] = Serial.read();
  }
  debug(F("Received data: "));
  for (byte i = 0; i < size; i++) {
    debugnlf(F("Data["));
    debugnlf(String(i));
    debugnlf(F("]: "));
    debug(String(data[i]));
  }

  debug(F("Provide checksum of command:"));
  waitForSerialData(1);
  byte checksum = Serial.read();
  debugnlf(F("Received checksum of command: "));
  debug(String(checksum));

  switch (type) {
    case HEX_TYPE_DATA:
      debug(F("Parsing command of type DATA"));
      if (extended_linear_address) {
        debug(F("We are in EXTEDNED LINEAR ADDRESS. That means we will program configuration or data of program."));
        if ((address >= 0 && address + size/2 <= 0x4) || (address >= 0x7 && address + size/2 <= 0x9)) {
          go_to_configuration_address();
          if (!load_data_for_program_memory(size, address+0x8000, data)) {
            handle_error(ERROR_CODE_LOAD_CONFIGURATION_VERIFICATION_FAILED);
            return;
          }
        } else if (address >= 0x7000 && address + size/2 <= 0x7100) {
          if (!load_data_for_data_memory(size, address & 0x01FF, data)) {
            handle_error(ERROR_CODE_LOAD_DATA_VERIFICATION_FAILED);
            return;
          }
        } else {
          handle_error(ERROR_CODE_UNSUPPORTED_ADDRESS);
          return;
        }
      } else {
        debug(F("Loading data into program memory"));
        if (address >= 0 && address + size/2 <= 0x2000) {
          if (!load_data_for_program_memory(size, address, data)) {
            handle_error(ERROR_CODE_LOAD_PROGRAM_VERIFICATION_FAILED);
            return;
          }
        } else {
          handle_error(ERROR_CODE_UNSUPPORTED_ADDRESS);
          return;
        }
      }
      break;

    case HEX_TYPE_EXTENDED_LINEAR_ADDRESS:
      debug("Parsing command of type EXTENDED LINEAR ADDRESS");
      if (size != 2) {
        handle_error(ERROR_CODE_WRONG_SIZE_OF_DATA);
        return;
      }
      if (data[0] != 0x0 || data[1] != 0x1) {
        handle_error(ERROR_CODE_WRONG_DATA);
        return;
      }
      debug("All ok, extended linear address = 1");
      extended_linear_address = 1;
      break;
    case HEX_TYPE_END_OF_FILE:
      Serial.print(F("Done!"));
      return;
    case HEX_TYPE_EXTENDED_SEGMENT_ADDRESS:
      handle_error(ERROR_CODE_02_HEX_NOT_SUPPORTED);
      return;

    case HEX_TYPE_START_SEGMENT_ADDRESS:
      handle_error(ERROR_CODE_03_HEX_NOT_SUPPORTED);
      return;

    case HEX_TYPE_START_LINEAR_ADDRESS:
      handle_error(ERROR_CODE_05_HEX_NOT_SUPPORTED);
      return;

    default:
      handle_error(ERROR_CODE_UNRECOGNIZED_HEX_TYPE);
      return;
  }

  //everything is ok, respond to computer with expected response
  Serial.write(data, size);
  Serial.write(checksum);
}

bool verify(byte lsb, byte msb, int read) {
  debugnlf(F("Verify: writed: "));
  debugnlf(String(((msb & 0X3f) << 8) | lsb));
  debugnlf(" readed: ");
  debug(String(read));

  #if SIMULATE_SUCCESS
  return true
  #else
  return read == (((msb & 0X3f) << 8) | lsb);
  #endif
}

//################################
//COMPUTER COMMUNICATION FUNCTIONS
//################################
void waitForSerialData(int requestedBytesNumber) {
  while (Serial.available() < requestedBytesNumber) {
    delayMicroseconds(10);
  }
}

void handle_error(int code) {
  if (code == ERROR_CODE_NONE) return;
  Serial.print("Error");
  while (!Serial.available()) { delay(1); }
  Serial.read();

  switch (code) {
    case ERROR_CODE_TEST:
      Serial.write(51);  //size of message
      Serial.print("Test error code! Should never happen in production.");
      break;
    case ERROR_CODE_02_HEX_NOT_SUPPORTED:
      Serial.write(57);  //size of message
      Serial.print("Hex file contains entry type 0x02 which is not supported.");
      break;
    case ERROR_CODE_03_HEX_NOT_SUPPORTED:
      Serial.write(57);  //size of message
      Serial.print("Hex file contains entry type 0x03 which is not supported.");
      break;
    case ERROR_CODE_05_HEX_NOT_SUPPORTED:
      Serial.write(57);  //size of message
      Serial.print("Hex file contains entry type 0x05 which is not supported.");
      break;
    case ERROR_CODE_UNRECOGNIZED_HEX_TYPE:
      Serial.write(79);  //size of message
      Serial.print("Hex file contains wrong entry type. Only allowed Intel HEX Types are 0x00-0x05.");
      break;
    case ERROR_CODE_WRONG_SIZE_OF_DATA:
      Serial.write(40);  //size of message
      Serial.print("Received Intel Hex entry has wrong size.");
      break;
    case ERROR_CODE_WRONG_DATA:
      Serial.write(40);  //size of message
      Serial.print("Received Intel Hex entry has wrong data.");
      break;
    case ERROR_CODE_UNSUPPORTED_ADDRESS:
      Serial.write(60);  //size of message
      Serial.print("Received Intel Hex entry has address which is not supported.");
      break;
    case ERROR_CODE_LOAD_CONFIGURATION_VERIFICATION_FAILED:
      Serial.write(54);  //size of message
      Serial.print("Verification failed when writing configuration to PIC.");
      break;
    case ERROR_CODE_LOAD_DATA_VERIFICATION_FAILED:
      Serial.write(45);  //size of message
      Serial.print("Verification failed when writing data to PIC.");
      break;
    case ERROR_CODE_LOAD_PROGRAM_VERIFICATION_FAILED:
      Serial.write(48);  //size of message
      Serial.print("Verification failed when writing program to PIC.");
      break;
    default:
      Serial.write(56);
      Serial.print("Internal arduino error. Received unsupported error code.");
  }
}


void debug(String message) {
  if (DEBUG) {
    Serial.println(message);
  }
}

void debugnlf(String message) {
  if (DEBUG) {
    Serial.print(message);
  }
}

//########################
//HIGH LEVEL PIC FUNCTIONS
//########################

void read_id() {
  go_to_configuration_address();
  for (int i = 0; i < 10; i++) {
    Serial.print("Address ");
    Serial.print(i);
    Serial.print(": 0b");
    int data = read_program();
    Serial.print(data, BIN);
    Serial.print(" 0x");
    Serial.println(data, HEX);
    increment_address();
  }
  reset_address();
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

bool load_data_for_program_memory(byte size, int address, byte *data) {
  go_to_address(address);
  for (byte i = 0; i < size; i += 2) {
    debugnlf(F("Programming two bytes starting from: "));
    debug(String(i));
    one_word_program_cycle(data[i], data[i + 1]);
    if (verify(data[i], data[i + 1], read_program())) {
      increment_address();
    } else {
      return false;
    }
  }
  return true;
}

void one_word_program_cycle(byte lsb, byte msb) {
  send_command(LOAD_DATA_FOR_PROGRAM_MEMORY);
  write_two_bytes(lsb, msb);
  send_command(BEGIN_INTERNALLY_TIMED_PROG);
  delay(10);
}

void data_program_cycle(byte lsb, byte msb) {
  send_command(LOAD_DATA_FOR_DATA_MEMORY);
  write_two_bytes(lsb, msb);
  send_command(BEGIN_INTERNALLY_TIMED_PROG);
  delay(10);
}

void go_to_address(int address) {
  debugnlf(F("Going from address: "));
  debugnlf(String(current_address));
  debugnlf(F(" to address: "));
  debug(String(address));
  debugnlf(F("Extended Linear Address:"));
  debug(String(extended_linear_address));
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

void erase_chip() {
  go_to_configuration_address();
  send_command(BULK_ERASE_PROGRAM_MEMORY);
  delay(10);
  send_command(BULK_ERASE_DATA_MEMORY);
  delay(10);
  reset_address();
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
  extended_linear_address=0;
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
