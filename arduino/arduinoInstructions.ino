#include "Arduino.h"
#include <SoftwareSerial.h>
#include <Servo.h>

//Create servo object
Servo myservo1; // Rotating motor
Servo myservo2; // Fire motor

const byte rxPin = 9;
const byte txPin = 8;
SoftwareSerial BTSerial(rxPin, txPin); // RX TX

void setup() {
  // define pin modes for tx, rx:
  pinMode(rxPin, INPUT);
  pinMode(txPin, OUTPUT);
  BTSerial.begin(9600);
  Serial.begin(9600);

  //Set servo pin
  myservo1.attach(10);
  myservo2.attach(11);
  myservo1.write(90);  // set servo to mid-point
  myservo1.write(0);  // set servo to end-point - may change later
  pinMode(10,OUTPUT);
  pinMode(11,OUTPUT);
}

String messageBuffer = "";
String message = "";
int num = 0;

String extract = "";
char sign = '\0';
char bufferString[3];
int angle;

int angle1;
int angle2;

void loop() {

  while (BTSerial.available() > 0) {
    char data = (char) BTSerial.read();
    messageBuffer += data;
    if (data == ';'){
      num = 0;
      message = messageBuffer;
      messageBuffer = "";
      //Serial.print(message); // send to serial monitor
      //message = message + " ";    //For more even spacing on display, but doesn't actually matter
      //BTSerial.print(message); // send back to bluetooth terminal

      num = message.length();

      extract = message.substring(num - 3,num - 1);   //Extracts number only
      sign = message[num - 4];

      //BTSerial.println(num);
      //BTSerial.print(extract);

      extract.toCharArray(bufferString, extract.length()+1);
      angle = atoi(bufferString);

      if (sign == '-'){
        angle = -angle;       //Make angle negative, if negative sign is present
      }

      BTSerial.print(angle);

      fire(angle);    //angle should only be between -90 and 90
    }
  }
}

void fire (int angle) {

  delay(20);

  //Turn gun
  myservo1.write(90 + angle);                  // sets the servo position should be 0 to 180
  delay(500);                           // waits for the servo to get there

  //Move stopper to fire
  myservo2.write(45);                    // sets the servo position should be 0 to 180
  delay(500);                           // waits for the servo to get there

  //Put stopper back
  myservo2.write(0);                    // sets the servo position back to home
  delay(500);                           // waits for the servo to get there

  //Move gun back
  myservo1.write(90);                  // sets the servo position back to home
  delay(500);                           // waits for the servo to get there

}