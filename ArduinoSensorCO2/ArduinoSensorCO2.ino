/*
 * Simple air sensor device based on CCS 881. Measures CO2e and TVOC.  
 * Unit performs measurements and displays corresponding values in LCD.
 * Status LEDs indicate if CO2 value is in normal or high range.
 * All readings are sent via serial protocol to PC, where companion program 
 * displays the readings in real-time.
 * 
 * Hugo
 * 21/11/2020
 */

// Libraries 
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <I2Cdev.h>
#include "Adafruit_CCS811.h"

/* Start of USER settings */
 
// LED PINS
const uint8_t PIN_GREEN_LED = 2;
const uint8_t PIN_RED_LED = 3;

// LCD SPECS
const uint8_t LCD_ROWS = 2;
const uint8_t LCD_COLS = 16;

// CCS SENSOR CONFIGURATION
const uint16_t LowCO2 = 250;               // Minimum CO2 Level
const uint16_t MediumCO2 = 800;            // Medium CO2 Level
const uint16_t MaxCO2 = 1000;              // Maximum CO2 Level

// Periods of read and write
const uint16_t periodReadSensor = 2000;   // How often measurements are made (ms)
const uint16_t periodWritePC = 5000;     // How often values are sent to PC (ms)

//  Baud rate for serial port
const uint32_t baudRate = 57600;  

/* End of USER Settings */

// LCD Object
LiquidCrystal_I2C lcd(0x27,LCD_COLS,LCD_ROWS);

// CCS Sensor
Adafruit_CCS811 ccs;

// Global variables
float temp;
uint16_t valueCO2, valueTVOC;
uint8_t statusCCS;
uint32_t timeStart, timeNow, timeSinceStart;
bool serialState = false;

/* SETUP */
void setup() {

  // Set LED pins and turn on LEDs
  pinMode(PIN_GREEN_LED, OUTPUT);
  pinMode(PIN_RED_LED, OUTPUT);
  digitalWrite(PIN_GREEN_LED, HIGH);
  digitalWrite(PIN_RED_LED, HIGH);
    
  // Initialize Serial
  Serial.begin(baudRate);
  
  // Initialize LCD
  lcd.init();
  lcd.backlight();
  Write2LinesLCD("Hallo! :)", "CO2 sensor v1");

  // Switch off LEDs
  delay(200);
  digitalWrite(PIN_GREEN_LED, LOW);
  digitalWrite(PIN_RED_LED, LOW);
  
  // Initialize CCS sensor
  statusCCS = ccs.begin();
  if(!statusCCS){
    Write2LinesLCD("ERROR:", "Init CCS sensor");
    BlinkForeverLED(PIN_RED_LED,200);
  }

  // Calibrate temperature sensor
  while(!ccs.available());
  //temp = ccs.calculateTemperature(); // This board does not have temperature sensor
  ccs.setTempOffset(0);

  // Start counting time
  timeStart = millis();

}  


// MAIN PROGRAM
void loop() {

  // Keep track of last events
  static uint32_t timeLastRead = 0, timeLastWrite = 0; 
  
  // Read values from CCS sensor
  // Send values to LCD
  timeNow = millis();
  bool timeReadFlag = (timeNow - timeLastRead) > periodReadSensor;
  if(timeReadFlag){
    timeLastRead = timeNow;
    timeSinceStart = timeNow - timeStart;
    ReadCCS(temp, valueCO2, valueTVOC, statusCCS);
    WriteValuesLCD(temp,valueCO2,valueTVOC);
  }

  // Write "alarm" state to LCD and LEDs
  // Needs to be outside of previous if construct, so that LEDs can blink 
  StateCO2(valueCO2,timeReadFlag);

  // Check for request for sending serial data
  CheckSerial(serialState);
  
  // Send serial data if conditions are met
  bool timeWriteFlag = (timeNow - timeLastWrite) > periodWritePC;
  if(serialState && timeWriteFlag){
    timeLastWrite = timeNow;
    WriteValuesPC(timeSinceStart,temp,valueCO2,valueTVOC);
  }
   
}

/* Reads values from CCS sensor */
void ReadCCS(float &temp, uint16_t &valueCO2, uint16_t &valueTVOC, uint8_t &statusCCS){
  if(ccs.available()){
    statusCCS = ccs.readData();
    if(statusCCS<1){
      valueCO2 = ccs.geteCO2();
      valueTVOC = ccs.getTVOC();
    }
  }
}

/* Writes CO2 state to LEDs and LCD */
void StateCO2(uint16_t valueCO2, bool timeReadFlag){
  
  static uint8_t resultLast = 0;
  uint8_t result = 1;  
  
  if (valueCO2<LowCO2){
    result = 1;
    BlinkLED(PIN_GREEN_LED,200);
    digitalWrite(PIN_RED_LED,LOW);
    if(timeReadFlag && result!=resultLast){
      WriteLineLCD("Stabilizing...",2);
    }
  }
  else if ((valueCO2>=LowCO2) && (valueCO2<=MediumCO2)){
    result = 2;
    digitalWrite(PIN_GREEN_LED,HIGH);
    digitalWrite(PIN_RED_LED,LOW);
    if(timeReadFlag && result!=resultLast){
      WriteLineLCD("Level OK",2);
    }
  }
  else if ((valueCO2>MediumCO2) && (valueCO2<MaxCO2)){
    result = 3;
    digitalWrite(PIN_GREEN_LED,HIGH);
    BlinkLED(PIN_RED_LED,1000);
    if(timeReadFlag && result!=resultLast){
      WriteLineLCD("Prepare to vent.",2);
    }
  }
  else if (valueCO2>=MaxCO2){
    result = 4;
    digitalWrite(PIN_GREEN_LED,LOW);
    BlinkLED(PIN_RED_LED,200);
    if(timeReadFlag && result!=resultLast){
      WriteLineLCD("Vent. now!",2);
    }
  }
  resultLast = result;
}

/* Makes LED blink FOREVER */
void BlinkForeverLED(uint8_t pin, uint16_t duration){
  while(true){
  digitalWrite(pin, HIGH);
  delay(duration);
  digitalWrite(pin, LOW);
  delay(duration);
  }
}

/* Makes LED blink for specified duration */
void BlinkLED(uint8_t pin, uint16_t duration){
  static byte ledState = 1;
  static unsigned long previousTime = millis();
  unsigned long currentTime = millis();
  if (currentTime - previousTime >= duration){
    previousTime = currentTime;
    ledState ^= 1;
    digitalWrite(pin, ledState);
  }
}

/* 
 *  Writes sensor values to LCD
 *  Currently only CO2 value is displayed
 *  Can be extended to display temperature and TVOC
 */
void WriteValuesLCD(float temp, uint16_t valueCO2, uint16_t valueTVOC){
  const uint8_t line = 1;
  ClearLineLCD(line);
  lcd.print("CO2:");
  lcd.setCursor(4,line-1);
  if(valueCO2<100){
    lcd.setCursor(8,0);
  }
  else if(valueCO2<1000){
    lcd.setCursor(7,0);
  }
  else{
    lcd.setCursor(6,0);
  }
  lcd.print(valueCO2);
  lcd.setCursor(11,0);
  lcd.print("ppm");
}

/* Clears LCD line and places cursor at start position */
void ClearLineLCD(uint8_t line){
  lcd.setCursor(0,line-1);
  for(uint8_t i=0; i < LCD_COLS; i++){
    lcd.print(" ");
  }
  lcd.setCursor(0,line-1);
}

/* Writes single line of text to specified LCD line */
void WriteLineLCD(String text, uint8_t line){
  ClearLineLCD(line);
  lcd.print(text);
}

/* Writes 2 lines of text to LCD */
void Write2LinesLCD(String text1, String text2){
  lcd.clear();
  lcd.setCursor(0,0);
  lcd.print(text1);
  lcd.setCursor(0,1);
  lcd.print(text2);
}

/* Writes values to PC */
void WriteValuesPC(uint32_t tempo, float temp, uint16_t valueCO2, uint16_t valueTVOC){
  const char sep = ' ';
  Serial.print(tempo);
  Serial.print(sep);
  Serial.print(temp);
  Serial.print(sep);
  Serial.print(valueCO2);
  Serial.print(sep);
  Serial.println(valueTVOC);
}

/* Checks if there is request for transmission */
void CheckSerial(bool &result){
  const char key = 'H';
  if(false){ // Reserved for future use
    return;
  }
  else{
      if(Serial.available()>0){
        char ch = Serial.read();
        if(ch == key){
          result = true;
          Serial.println("<H>");
        }
    }
  }
}
