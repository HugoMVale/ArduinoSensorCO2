import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.awt.Frame; 
import java.awt.BorderLayout; 
import processing.serial.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class PCPlotterSensorCO2 extends PApplet {

/*
Processing code to plot data from air sensor in real time
Heavily inspired from the example code of the GraphClass.
See: https://github.com/sebnil/RealtimePlotter

Hugo
20/11/2020
*/

// Import libraries




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
int[] graphColors = new int[nSeries];
boolean[][] lineVisible = new boolean[nSubplots][nSeries];
float[] yMax = new float[nSubplots];
float[] yMin = new float[nSubplots];
float xMin;

/* SETUP */
public void setup() {
  
  surface.setTitle("Real-time Air Sensor");
  
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

public void draw() {
  
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
      timeNow = PApplet.parseFloat(myReadings[0]);
      nums = subset(PApplet.parseFloat(myReadings),1);
  
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
      int nX = ceil(PApplet.parseFloat(nReadings*timeStep)/60/timeUpdate);
      xMin = -PApplet.parseFloat(timeUpdate*nX);
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
            indexStart = max(0,indexStart);
            LineGraph[j].LineGraph(subset(lineGraphSampleNumbers,indexStart), subset(yValues,indexStart));
          }
        }
      }    
    }
  
}

// Initialize plots
public void initPlots(){
  
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

public void findArduino(){
  
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
  
/*   =================================================================================       
     The Graph class contains functions and variables that have been created to draw 
     graphs. Here is a quick list of functions within the graph class:
          
       Graph(int x, int y, int w, int h,color k)
       DrawAxis()
       Bar([])
       smoothLine([][])
       DotGraph([][])
       LineGraph([][]) 
     
     =================================================================================*/   

    
    class Graph 
    {
      
      boolean Dot=true;            // Draw dots at each data point if true
      boolean RightAxis;            // Draw the next graph using the right axis if true
      boolean ErrorFlag=false;      // If the time array isn't in ascending order, make true  
      boolean ShowMouseLines=true;  // Draw lines and give values of the mouse position
    
      int     xDiv=5,yDiv=5;            // Number of sub divisions
      int     xPos,yPos;            // location of the top left corner of the graph  
      int     Width,Height;         // Width and height of the graph
    

      int   GraphColor;
      int   BackgroundColor=color(255);  
      int   StrokeColor=color(180);     
      
      String  Title="Title";          // Default titles
      String  xLabel="x - Label";
      String  yLabel="y - Label";

      float   yMax=1024, yMin=0;      // Default axis dimensions
      float   xMax=10, xMin=0;
      float   yMaxRight=1024,yMinRight=0;
  
      PFont   Font;                   // Selected font used for text 
      
  //    int Peakcounter=0,nPeakcounter=0;
     
      Graph(int x, int y, int w, int h,int k) {  // The main declaration function
        xPos = x;
        yPos = y;
        Width = w;
        Height = h;
        GraphColor = k;
        
      }
    
     
       public void DrawAxis(){
       
   /*  =========================================================================================
        Main axes Lines, Graph Labels, Graph Background
       ==========================================================================================  */
    
        fill(BackgroundColor); color(0);stroke(StrokeColor);strokeWeight(1);
        int t=60;
        
        rect(xPos-t*1.6f,yPos-t,Width+t*2.5f,Height+t*2);            // outline
        textAlign(CENTER);textSize(18);
        float c=textWidth(Title);
        fill(BackgroundColor); color(0);stroke(0);strokeWeight(1);
        rect(xPos+Width/2-c/2,yPos-35,c,0);                         // Heading Rectangle  
        
        fill(0);
        text(Title,xPos+Width/2,yPos-37);                            // Heading Title
        textAlign(CENTER);textSize(14);
        text(xLabel,xPos+Width/2,yPos+Height+t/1.5f);                     // x-axis Label 
        
        rotate(-PI/2);                                               // rotate -90 degrees
        text(yLabel,-yPos-Height/2,xPos-t*1.6f+20);                   // y-axis Label  
        rotate(PI/2);                                                // rotate back
        
        textSize(10); noFill(); stroke(0); smooth();strokeWeight(1);
          //Edges
          line(xPos-3,yPos+Height,xPos-3,yPos);                        // y-axis line 
          line(xPos-3,yPos+Height,xPos+Width+5,yPos+Height);           // x-axis line 
          
           stroke(200);
          if(yMin<0){
                    line(xPos-7,                                       // zero line 
                         yPos+Height-(abs(yMin)/(yMax-yMin))*Height,   // 
                         xPos+Width,
                         yPos+Height-(abs(yMin)/(yMax-yMin))*Height
                         );
          
                    
          }
          
          if(RightAxis){                                       // Right-axis line   
              stroke(0);
              line(xPos+Width+3,yPos+Height,xPos+Width+3,yPos);
            }
            
           /*  =========================================================================================
                Sub-devisions for both axes, left and right
               ==========================================================================================  */
            
            stroke(0);
            
           for(int x=0; x<=xDiv; x++){
       
            /*  =========================================================================================
                  x-axis
                ==========================================================================================  */
             
            line(PApplet.parseFloat(x)/xDiv*Width+xPos-3,yPos+Height,       //  x-axis Sub devisions    
                 PApplet.parseFloat(x)/xDiv*Width+xPos-3,yPos+Height+5);     
                 
            textSize(10);                                      // x-axis Labels
            String xAxis=str(xMin+PApplet.parseFloat(x)/xDiv*(xMax-xMin));  // the only way to get a specific number of decimals 
            String[] xAxisMS=split(xAxis,'.');                 // is to split the float into strings 
            text(xAxisMS[0]+"."+xAxisMS[1].charAt(0),          // ...
                 PApplet.parseFloat(x)/xDiv*Width+xPos-3,yPos+Height+15);   // x-axis Labels
          }
          
          
           /*  =========================================================================================
                 left y-axis
               ==========================================================================================  */
          
          for(int y=0; y<=yDiv; y++){
            line(xPos-3,PApplet.parseFloat(y)/yDiv*Height+yPos,                // ...
                  xPos-7,PApplet.parseFloat(y)/yDiv*Height+yPos);              // y-axis lines 
            
            textAlign(RIGHT);fill(20);
            
            String yAxis=str(yMin+PApplet.parseFloat(y)/yDiv*(yMax-yMin));     // Make y Label a string
            String[] yAxisMS=split(yAxis,'.');                    // Split string
           
            text(yAxisMS[0]+"."+yAxisMS[1].charAt(0),             // ... 
                 xPos-15,PApplet.parseFloat(yDiv-y)/yDiv*Height+yPos+3);       // y-axis Labels 
                        
                        
            /*  =========================================================================================
                 right y-axis
                ==========================================================================================  */
            
            if(RightAxis){
             
              color(GraphColor); stroke(GraphColor);fill(20);
            
              line(xPos+Width+3,PApplet.parseFloat(y)/yDiv*Height+yPos,             // ...
                   xPos+Width+7,PApplet.parseFloat(y)/yDiv*Height+yPos);            // Right Y axis sub devisions
                   
              textAlign(LEFT); 
            
              String yAxisRight=str(yMinRight+PApplet.parseFloat(y)/                // ...
                                yDiv*(yMaxRight-yMinRight));           // convert axis values into string
              String[] yAxisRightMS=split(yAxisRight,'.');             // 
           
               text(yAxisRightMS[0]+"."+yAxisRightMS[1].charAt(0),     // Right Y axis text
                    xPos+Width+15,PApplet.parseFloat(yDiv-y)/yDiv*Height+yPos+3);   // it's x,y location
            
              noFill();
            }stroke(0);
            
          
          }
          
 
      }
      
      
   /*  =========================================================================================
       Bar graph
       ==========================================================================================  */   
      
      public void Bar(float[] a ,int from, int to) {
        
         
          stroke(GraphColor);
          fill(GraphColor);
          
          if(from<0){                                      // If the From or To value is out of bounds 
           for (int x=0; x<a.length; x++){                 // of the array, adjust them 
               rect(PApplet.parseInt(xPos+x*PApplet.parseFloat(Width)/(a.length)),
                    yPos+Height-2,
                    Width/a.length-2,
                    -a[x]/(yMax-yMin)*Height);
                 }
          }
          
          else {
          for (int x=from; x<to; x++){
            
            rect(PApplet.parseInt(xPos+(x-from)*PApplet.parseFloat(Width)/(to-from)),
                     yPos+Height-2,
                     Width/(to-from)-2,
                     -a[x]/(yMax-yMin)*Height);
                     
    
          }
          }
          
      }
  public void Bar(float[] a ) {
  
              stroke(GraphColor);
          fill(GraphColor);
    
  for (int x=0; x<a.length; x++){                 // of the array, adjust them 
               rect(PApplet.parseInt(xPos+x*PApplet.parseFloat(Width)/(a.length)),
                    yPos+Height-2,
                    Width/a.length-2,
                    -a[x]/(yMax-yMin)*Height);
                 }
          }
  
  
   /*  =========================================================================================
       Dot graph
       ==========================================================================================  */   
       
        public void DotGraph(float[] x ,float[] y) {
          
         for (int i=0; i<x.length; i++){
                    strokeWeight(2);stroke(GraphColor);noFill();smooth();
           ellipse(
                   xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width,
                   yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height,
                   2,2
                   );
         }
                             
      }
      
   /*  =========================================================================================
       Streight line graph 
       ==========================================================================================  */
       
      public void LineGraph(float[] x ,float[] y) {
          
         for (int i=0; i<(x.length-1); i++){
                    strokeWeight(2);stroke(GraphColor);noFill();smooth();
           line(xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width,
                                            yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height,
                                            xPos+(x[i+1]-x[0])/(x[x.length-1]-x[0])*Width,
                                            yPos+Height-(y[i+1]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height);
         }
                             
      }
      
      /*  =========================================================================================
             smoothLine
          ==========================================================================================  */
    
      public void smoothLine(float[] x ,float[] y) {
         
        float tempyMax=yMax, tempyMin=yMin;
        
        if(RightAxis){yMax=yMaxRight;yMin=yMinRight;} 
         
        int counter=0;
        int xlocation=0,ylocation=0;
         
//         if(!ErrorFlag |true ){    // sort out later!
          
          beginShape(); strokeWeight(2);stroke(GraphColor);noFill();smooth();
         
            for (int i=0; i<x.length; i++){
              
           /* ===========================================================================
               Check for errors-> Make sure time array doesn't decrease (go back in time) 
              ===========================================================================*/
              if(i<x.length-1){
                if(x[i]>x[i+1]){
                   
                  ErrorFlag=true;
                
                }
              }
         
         /* =================================================================================       
             First and last bits can't be part of the curve, no points before first bit, 
             none after last bit. So a streight line is drawn instead   
            ================================================================================= */ 

              if(i==0 || i==x.length-2)line(xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width,
                                            yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height,
                                            xPos+(x[i+1]-x[0])/(x[x.length-1]-x[0])*Width,
                                            yPos+Height-(y[i+1]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height);
                                            
          /* =================================================================================       
              For the rest of the array a curve (spline curve) can be created making the graph 
              smooth.     
             ================================================================================= */ 
                            
              curveVertex( xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width,
                           yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height);
                           
           /* =================================================================================       
              If the Dot option is true, Place a dot at each data point.  
             ================================================================================= */    
           
             if(Dot)ellipse(
                             xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width,
                             yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height,
                             2,2
                             );
                             
         /* =================================================================================       
             Highlights points closest to Mouse X position   
            =================================================================================*/ 
                          
              if( abs(mouseX-(xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width))<5 ){
                
                 
                  float yLinePosition = yPos+Height-(y[i]/(yMax-yMin)*Height)+(yMin)/(yMax-yMin)*Height;
                  float xLinePosition = xPos+(x[i]-x[0])/(x[x.length-1]-x[0])*Width;
                  strokeWeight(1);stroke(240);
                 // line(xPos,yLinePosition,xPos+Width,yLinePosition);
                  strokeWeight(2);stroke(GraphColor);
                  
                  ellipse(xLinePosition,yLinePosition,4,4);
              }
              
     
              
            }  
       
          endShape(); 
          yMax=tempyMax; yMin=tempyMin;
                float xAxisTitleWidth=textWidth(str(map(xlocation,xPos,xPos+Width,x[0],x[x.length-1])));
          
           
       if((mouseX>xPos&mouseX<(xPos+Width))&(mouseY>yPos&mouseY<(yPos+Height))){   
        if(ShowMouseLines){
              // if(mouseX<xPos)xlocation=xPos;
            if(mouseX>xPos+Width)xlocation=xPos+Width;
            else xlocation=mouseX;
            stroke(200); strokeWeight(0.5f);fill(255);color(50);
            // Rectangle and x position
            line(xlocation,yPos,xlocation,yPos+Height);
            rect(xlocation-xAxisTitleWidth/2-10,yPos+Height-16,xAxisTitleWidth+20,12);
            
            textAlign(CENTER); fill(160);
            text(map(xlocation,xPos,xPos+Width,x[0],x[x.length-1]),xlocation,yPos+Height-6);
            
           // if(mouseY<yPos)ylocation=yPos;
             if(mouseY>yPos+Height)ylocation=yPos+Height;
            else ylocation=mouseY;
          
           // Rectangle and y position
            stroke(200); strokeWeight(0.5f);fill(255);color(50);
            
            line(xPos,ylocation,xPos+Width,ylocation);
             int yAxisTitleWidth=PApplet.parseInt(textWidth(str(map(ylocation,yPos,yPos+Height,y[0],y[y.length-1]))) );
            rect(xPos-15+3,ylocation-6, -60 ,12);
            
            textAlign(RIGHT); fill(GraphColor);//StrokeColor
          //    text(map(ylocation,yPos+Height,yPos,yMin,yMax),xPos+Width+3,yPos+Height+4);
            text(map(ylocation,yPos+Height,yPos,yMin,yMax),xPos -15,ylocation+4);
           if(RightAxis){ 
                          
                           stroke(200); strokeWeight(0.5f);fill(255);color(50);
                           
                           rect(xPos+Width+15-3,ylocation-6, 60 ,12);  
                            textAlign(LEFT); fill(160);
                           text(map(ylocation,yPos+Height,yPos,yMinRight,yMaxRight),xPos+Width+15,ylocation+4);
           }
            noStroke();noFill();
         }
       }
            
   
      }

       
          public void smoothLine(float[] x ,float[] y, float[] z, float[] a ) {
           GraphColor=color(188,53,53);
            smoothLine(x ,y);
           GraphColor=color(193-100,216-100,16);
           smoothLine(z ,a);
   
       }
       
       
       
    }
    
 
  public void settings() {  size(890, 680); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "PCPlotterSensorCO2" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
