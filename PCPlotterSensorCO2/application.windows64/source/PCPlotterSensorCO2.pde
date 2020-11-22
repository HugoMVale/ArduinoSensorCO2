/*
Processing code to plot data from air sensor in real time
Heavily inspired from the example code of the GraphClass.
Hugo
20/11/2020
*/

// Import libraries
import java.awt.Frame;
import java.awt.BorderLayout;
import processing.serial.*;

// Serial port object
Serial arduinoPort;

// Plot setup variables
final int timeSpan = 3600*2; // store info up to 2 hours
final int timeStep = 5; // seconds : to be improved
final int nSubplots = 2;
final int nSeries = 3;
final int nSteps = timeSpan/timeStep;  
final int subplotWidth = 700;
final int subplotHeight = 200;

// Graph objects
Graph[] LineGraph = {new Graph(120,  70, subplotWidth, subplotHeight, color (20, 20, 200)),new Graph(120, 400, subplotWidth, subplotHeight, color (20, 20, 200))};

// Arrays to store plot info, etc.
float[][][] lineGraphValues = new float[nSubplots][nSeries][nSteps];
float[] lineGraphSampleNumbers = new float[nSteps];
color[] graphColors = new color[nSeries];
boolean[][] lineVisible = new boolean[nSubplots][nSeries];
float[] yMax = new float[nSubplots];
float[] yMin = new float[nSubplots];
float xMin;

/* SETUP */
void setup() {
  
  surface.setTitle("Real-time Air Sensor");
  size(890, 680);
   //<>//
  // Start serial communication
  findArduino();
  
  // Initiliaze plots
  initPlots();
} //<>// //<>//


// Initialize some variables
byte[] inBuffer = new byte[100];
float timeOld = 0;
float timeNow = 0;
int nReadings = 0;
boolean updatePlots = false;
float[] nums;

void draw() {
  
    /* Read serial and update values */
    if (arduinoPort.available() > 0) {
      try {
        arduinoPort.readBytesUntil('\r', inBuffer);
      }
      catch (Exception e) {
      }
      String myString = new String(inBuffer);
  
      // Split the string at delimiter (space)
      String[] myReadings = split(myString, ' ');
      
      // Split time from measured variables
      timeNow = float(myReadings[0]);
      nums = subset(float(myReadings),1);
  
    }
    
    // if there is a new reading, then plot is to be updated
    if(timeNow!=timeOld){
      timeOld = timeNow;
      nReadings = nReadings+1;
      updatePlots = true;
    }
    else{
      updatePlots = false;
    }
    
    /* Update plots */  
    if (updatePlots){
      
      // Update y values for plots
      for (int j=0; j<nSubplots; j++){ // for all subplots
        for (int i=0; i<nums.length; i++) { // for all readings 
          try {
            if (i<lineGraphValues[j].length) {
              for (int k=0; k<lineGraphValues[j][i].length-1; k++) {
                lineGraphValues[j][i][k] = lineGraphValues[j][i][k+1];
              } //<>//
              lineGraphValues[j][i][lineGraphValues[j][i].length-1] = nums[i];
            }
          }
          catch (Exception e) {
          }
        }
      }
      
      // Update plot lines
      int timeUpdate = 5;
      int nX = ceil(float(nReadings*timeStep)/60/timeUpdate);
      xMin = -float(timeUpdate*nX);
      int indexStart = 0;
      for (int j=0; j<nSubplots; j++){ //for all subplots
        LineGraph[j].xMin = xMin;
        LineGraph[j].DrawAxis();  
        for (int i=0;i<nSeries; i++) { // for all series
          LineGraph[j].GraphColor = graphColors[i];
          if (lineVisible[j][i]){
            float[] yValues = lineGraphValues[j][i];
            for (int k=0;k<yValues.length; k++){
              yValues[k] = max(yMin[j],min(yMax[j],yValues[k])); // otherwise it will plot beyond range 
            }
            indexStart = (nSteps-1) - nX*timeUpdate*60/timeStep; 
            LineGraph[j].LineGraph(subset(lineGraphSampleNumbers,indexStart), subset(yValues,indexStart));
          }
        }
      }    
    }
  
}

// Initialize plots
void initPlots(){
  
  // set line graph colors
  graphColors[0] = color(131, 255, 20);
  graphColors[1] = color(232, 158, 12);
  graphColors[2] = color(255, 0, 0);
  //graphColors[3] = color(62, 12, 232);
  //graphColors[4] = color(13, 255, 243);
  //graphColors[5] = color(200, 46, 232);
  
  lineVisible[0][0] = false;
  lineVisible[0][1] = true;
  lineVisible[0][2] = false;
  lineVisible[1][0] = false;
  lineVisible[1][1] = false;
  lineVisible[1][2] = true;

  final String xLabel = "Time [min]";
  final float xMax = 0;
  final float xMin0 = -1;
  final int xDiv = 10;
  
  int j=0; 
  LineGraph[j].yLabel = "CO2 [ppm]";
  yMax[j] = 2000;
  yMin[j] = 0; 
  
  j=1; 
  LineGraph[j].yLabel = "VOC [ppb]";
  yMax[j] = 100;
  yMin[j] = 0; 
 
  for (j=0; j<nSubplots; j++){
    LineGraph[j].xLabel = xLabel;
    LineGraph[j].Title = "";  
    LineGraph[j].xDiv = xDiv;  
    LineGraph[j].xMax = xMax; 
    LineGraph[j].xMin = xMin0;  
    LineGraph[j].yMax = yMax[j]; 
    LineGraph[j].yMin = yMin[j];
  }

  // build initial x-axis values
  for (j=0; j<nSubplots; j++){
    for (int i=0; i<lineGraphValues[j].length; i++) {
      for (int k=0; k<lineGraphValues[j][0].length; k++) {
        lineGraphValues[j][i][k] = 0;
        if (i==0)
          lineGraphSampleNumbers[k] = k; // change this to 0
      }
    }
  }

  // Draw empty plots
  for (j=0; j<nSubplots; j++){
    LineGraph[j].DrawAxis();
  }
  
}

void findArduino(){
  
  final int Baud = 57600;
  boolean portFound = false;
  boolean portOpen = false;
  String portName = null;
  char keySymbol = 'H';
  String expectedReply = "<H>";
  
  // Find all COM ports
  println("Scan open COM ports...");
  int nPorts = 0;
  while (nPorts<1){
    nPorts = Serial.list().length;
    if(nPorts<1){
      println("No COM ports in use. Rescanning...");
      delay(1000);
    }
  }
 
  println("Locating sensor...");
  println(Serial.list());
 
  // Send mesage to port and listen
  while (!portFound){
    
    portName = Serial.list()[nPorts-1];
    println("Connecting to -> " + portName);
    
    // Try to open port
    try{
      arduinoPort = new Serial(this, portName, Baud);
      arduinoPort.clear();
      arduinoPort.bufferUntil(10);
      portOpen = true;
    }
    catch (Exception e) {
      portOpen = false;      
      println("Exception connecting to " + portName);
      println(e);
    }
    
   
    // Send code character and listen
    int nTrials = 5;
    delay(10000); // Wait awhile for arduino to be running the main loop
    while(portOpen && !portFound && nTrials>0){
      arduinoPort.write(keySymbol);   //-- Send  code letter
      delay(100);
      if(arduinoPort.available()>0){
        String message = arduinoPort.readString();
        println(message);
        portFound = message.contains(expectedReply);
      }
      nTrials--;      
      println("Waiting for response from device on " + portName);
      delay(500);
    }
 
    if (!portFound){
      println("No response from device on " + portName);
      arduinoPort.clear();
      arduinoPort.stop();
      delay(200);
    }

    nPorts--;
    if (nPorts<1){
      nPorts = Serial.list().length;
    }
  }  
}
