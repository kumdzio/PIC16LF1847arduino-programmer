//commands
#define LOAD_CONFIGURATION            0b000000
#define LOAD_DATA_FOR_PROGRAM_MEMORY  0b000010
#define LOAD_DATA_FOR_DATA_MEMORY     0b000011
#define READ_DATA_FROM_PROGRAM_MEMORY 0b000100
#define READ_DATA_FROM_DATA_MEMORY    0b000101
#define INCREMENT_ADDRESS             0b000110
#define RESET_ADDRESS                 0b010110
#define BEGIN_INTERNALLY_TIMED_PROG   0b001000
#define BEGIN_EXTERNALLY_TIMED_PROG   0b011000
#define END_EXTERNALLY_TIMED_PROG     0b001010
#define BULK_ERASE_PROGRAM_MEMORY     0b001001
#define BULK_ERASE_DATA_MEMORY        0b001011
#define ROW_ERASE_PROGRAM_MEMORY      0b010001

#define CLK  2
#define DATA 3

int currentAddressLSB=0;
int currentAddressMSB=0;

void read_id();
void read_all();
void set_address();
void inc_address(int inc_times);
void go_to_configuration_address();
int read_program();
void send_command(byte command);
void clk_pulse();
void programming();
void waitForSerialData(int requestedBytesNumber);
void writeError(String s);

void setup() {
   Serial.begin(57600);
  pinMode(CLK, OUTPUT);
  pinMode(DATA,OUTPUT);
  digitalWrite(DATA,LOW);
  digitalWrite(CLK,LOW);
  pinMode(LED_BUILTIN,OUTPUT);
}

void loop() {
  byte command;
  if(Serial.available()){
    command = Serial.read();
    switch (command){
      case 'h':
        digitalWrite(LED_BUILTIN,!digitalRead(LED_BUILTIN));
        Serial.print("Arduino PIC16(L)F1847 programmer by Kumdzio");
      break;
      case 'i':
      Serial.println("Reading ID of device:");
      read_id();
      break;
      case 'p':
      programming();
      break;
    }
  }

}

void programming(){
  waitForSerialData(1);
  byte size = Serial.read();
  waitForSerialData(2);
  int address = Serial.read()<<8|Serial.read();
  waitForSerialData(1);
  byte type = Serial.read();
  if(type == 0x01){
    Serial.print("Done!");
    return;
  }
  byte data[size];
  for (byte i = 0; i<size; i++) {
  waitForSerialData(1);
    data[i] = Serial.read();
  }
  waitForSerialData(1);
  byte checksum = Serial.read();
  byte errorCode=0;

  if(address>200){
    errorCode=1;
  }

  if(errorCode==0){
    Serial.write(data, size);
    Serial.write(checksum);
  }else{
    Serial.print("Error");
    while(!Serial.available()){
      delay(1);
    }
    Serial.read();
    switch (errorCode) {
      case 1:
        Serial.write(51);
        Serial.print("Test error code! Should never happen in production.");
    }
  }

}

void read_id(){
  go_to_configuration_address();
  for(int i =0; i<10; i++){
    Serial.print("Address ");
    Serial.print(i);
    Serial.print(": 0b");
    int data = read_program();
    Serial.print(data, BIN);
    Serial.print(" 0x");
    Serial.println(data, HEX);
    send_command(INCREMENT_ADDRESS);
  }
  send_command(RESET_ADDRESS);

}

void read_all(){
  send_command(RESET_ADDRESS);
}

void set_address(){

}

void go_to_configuration_address(){
  digitalWrite(CLK,LOW);
  digitalWrite(DATA,LOW);
  delay(1);
  send_command(LOAD_CONFIGURATION);
  //empty data which goes into latch
  for(int i=0;i<16;i++){
    clk_pulse();
  }

}

int read_program(){
  int result = 0;
  send_command(READ_DATA_FROM_PROGRAM_MEMORY);
  pinMode(DATA, INPUT);
  clk_pulse();
  clk_pulse();
  for(int i = 0; i<14; i++){
    result |= digitalRead(DATA)<<i;
    clk_pulse();
  }
  return result;
}

void send_command(byte command){
  pinMode(DATA, OUTPUT);
  digitalWrite(CLK,LOW);
  digitalWrite(DATA,LOW);
  for(int i=0; i<6 ; i++){
    digitalWrite(DATA, command>>i&1);
    clk_pulse();
  }
  delay(1);
}

void clk_pulse(){
  digitalWrite(CLK,HIGH);
  digitalWrite(CLK,LOW);
}


void waitForSerialData(int requestedBytesNumber){
  while(Serial.available()<requestedBytesNumber){
    delayMicroseconds(10);
  }
}
