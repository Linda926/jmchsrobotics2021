/*----------------------------------------------------------------------------*/
/* Copyright (c) 2019 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystems;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.HopperConstants;
import frc.robot.Constants.HopperPIDs;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.AnalogInput;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;

// import com.ctre.phoenix.ParamEnum;

public class HopperSubsystem extends SubsystemBase {
  private final TalonSRX m_hopperMotor;

  private double kP = HopperPIDs.kP;
  private double kI = HopperPIDs.kI;
  private double kD = HopperPIDs.kD;
  private double kF = HopperPIDs.kF;
  private double kMaxOutput = HopperPIDs.MAX_OUTPUT;
  private double kMinOutput = HopperPIDs.MIN_OUTPUT;
  private double m_setpoint = 0;

  private long m_desiredDaisyPosition = 0;
  private Boolean m_daisyMoving = false;
  private Timer daisyTimeMoving;
  private Timer daisyTimeInPosition;
  private Timer daisyJammedTimer;
  private boolean daisyJammed = false;

  // photdiode work
  private final AnalogInput m_lightSensor = new AnalogInput(0);
  private Boolean photoIsDark = false;
  private double photoSamples[];
  private int photoSamplesI = 0;

  /**
   * The index is to see the rotation position on the robot
   * This will count up throughout the match, so if you want only the 0-6 range, use (daisyIndex%6)
   */
  private int daisyIndex;
  
  /**
   * Creates a new HopperSubsystem.
   */
  public HopperSubsystem() {
    kP = HopperPIDs.kP;
    kI = HopperPIDs.kI;
    kD = HopperPIDs.kD;
    kF = HopperPIDs.kF;
    
    m_hopperMotor = new TalonSRX(HopperConstants.HOPPER_MOTOR_ID);
  
    daisyIndex = 0;
    photoSamples = new double [ HopperConstants.PHOTO_NUM_SAMPLES];

    /* Factory Default all hardware to prevent unexpected behaviour */
    m_hopperMotor.configFactoryDefault();
    
		
    /* Config the sensor used for Primary PID and sensor direction */
    // CTRE's sample code uses _Relative, but as of 1/26 finding _Absolute works better for us.
    m_hopperMotor.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, // _Relative _Absolute,
                                            HopperPIDs.kPIDLoopIdx,
                                            HopperPIDs.kTimeoutMs);

    /* Ensure sensor is positive when output is positive */
		m_hopperMotor.setSensorPhase(HopperPIDs.kSensorPhase);

		/**
		 * Set based on what direction you want forward/positive to be.
		 * This does not affect sensor phase. 
		 */ 
		m_hopperMotor.setInverted(HopperPIDs.kMotorInvert);

		/* Config the peak and nominal outputs, 12V means full */
		m_hopperMotor.configNominalOutputForward(0, HopperPIDs.kTimeoutMs);
		m_hopperMotor.configNominalOutputReverse(0, HopperPIDs.kTimeoutMs);
		m_hopperMotor.configPeakOutputForward(kMaxOutput, HopperPIDs.kTimeoutMs);
		m_hopperMotor.configPeakOutputReverse(kMinOutput, HopperPIDs.kTimeoutMs);

		/**
		 * Config the allowable closed-loop error, Closed-Loop output will be
		 * neutral within this range. See Table in Section 17.2.1 for native
		 * units per rotation.
		 */
		m_hopperMotor.configAllowableClosedloopError(HopperPIDs.kPIDLoopIdx, HopperConstants.ALLOWABLE_ERROR, HopperPIDs.kTimeoutMs);

		/* Config Position Closed Loop gains in slot0, tsypically kF stays zero. */
		m_hopperMotor.config_kF(HopperPIDs.kPIDLoopIdx, kF, HopperPIDs.kTimeoutMs);
		m_hopperMotor.config_kP(HopperPIDs.kPIDLoopIdx, kP, HopperPIDs.kTimeoutMs);
		m_hopperMotor.config_kI(HopperPIDs.kPIDLoopIdx, kI, HopperPIDs.kTimeoutMs);
		m_hopperMotor.config_kD(HopperPIDs.kPIDLoopIdx, kD, HopperPIDs.kTimeoutMs);
    
    m_setpoint = m_hopperMotor.getSensorCollection().getPulseWidthPosition();
    
    resetReference();  // not used between 1/26/20 and 2/14/20.
    //m_hopperMotor.set(ControlMode.Position, HopperConstants.DAISY_OFFSET);
    
    if(HopperPIDs.TUNE){
      SmartDashboard.putNumber("Hopper setpoint", m_setpoint);
      SmartDashboard.putNumber("Hopper error between setpoint and encoder position", m_hopperMotor.getClosedLoopError());
      SmartDashboard.putNumber("Hopper P", kP);
      SmartDashboard.putNumber("Hopper I", kI);
      SmartDashboard.putNumber("Hopper D", kD);
        //      SmartDashboard.putNumber("Hopper I Zone", kIz);
      SmartDashboard.putNumber("Hopper Feed Forward", kF);
      SmartDashboard.putNumber("Hopper Max Output", kMaxOutput);
      SmartDashboard.putNumber("Hopper Min Output", kMinOutput);

      SmartDashboard.putNumber("Daisy Index", daisyIndex); // Puts the dasiy index on SmartDash
    }
    
  }

  /**
   * from CTRE's sample code https://github.com/CrossTheRoadElec/Phoenix-Examples-Languages/blob/master/Java/PositionClosedLoop/src/main/java/frc/robot/Robot.java.  
   * Not used as of 1/26/20.
   * Grab the 360 degree position of the MagEncoder's absolute
   * position, and intitally set the relative sensor to match.
   */
  public void resetReference(){
    int absolutePosition = m_hopperMotor.getSensorCollection().getPulseWidthPosition();
    m_setpoint = absolutePosition; // m_hopperMotor.getSelectedSensorPosition(HopperPIDs.kPIDLoopIdx); // getSensorCollection().getPulseWidthPosition();//getPosition();

    // Mask out overflows, keep bottom 12 bits 
		absolutePosition &= 0xFFF;
		if (HopperPIDs.kSensorPhase) { absolutePosition *= -1; }
		if (HopperPIDs.kMotorInvert) { absolutePosition *= -1; }

    // Set the quadrature (relative) sensor to match absolute
		m_hopperMotor.setSelectedSensorPosition(absolutePosition, HopperPIDs.kPIDLoopIdx, HopperPIDs.kTimeoutMs);
    
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    if(HopperPIDs.TUNE){
      double p = SmartDashboard.getNumber("Hopper P", 0);
      double i = SmartDashboard.getNumber("Hopper I", 0);
      double d = SmartDashboard.getNumber("Hopper D", 0);
      // double iz = SmartDashboard.getNumber("Hopper I Zone", 0);
      double ff = SmartDashboard.getNumber("Hopper Feed Forward", 0);
      double max = SmartDashboard.getNumber("Hopper Max Output", 0);
      double min = SmartDashboard.getNumber("Hopper Min Output", 0);
      SmartDashboard.putNumber("Hopper setpoint", m_setpoint);
      SmartDashboard.putNumber("Hopper error between setpoint and encoder position", m_hopperMotor.getClosedLoopError());
      SmartDashboard.putNumber("Hopper encoder position from getSelectedSensorPosition()", m_hopperMotor.getSelectedSensorPosition());

      // if PID coefficients on SmartDashboard have changed, write new values to controller
      if((p != kP)) { 
         kP = p; 	
         m_hopperMotor.config_kP(HopperPIDs.kPIDLoopIdx, kP, HopperPIDs.kTimeoutMs);
        }
      if((i != kI)) { 
        kI = i; 
        m_hopperMotor.config_kI(HopperPIDs.kPIDLoopIdx, kI, HopperPIDs.kTimeoutMs); 
      }
      if((d != kD)) {
        kD = d;
        m_hopperMotor.config_kD(HopperPIDs.kPIDLoopIdx, kD, HopperPIDs.kTimeoutMs);
       }
      //if((iz != kIz)) { m_hopperMotor.setIZone(iz); 
      //  kIz = iz; }
      if((ff != kF)) { 
        kF = ff; 
        m_hopperMotor.config_kF(HopperPIDs.kPIDLoopIdx, kF, HopperPIDs.kTimeoutMs);
      }
      if(max != kMaxOutput) {
        kMaxOutput = max;
        m_hopperMotor.configPeakOutputForward(kMaxOutput, HopperPIDs.kTimeoutMs);
      }
      if( min != kMinOutput){
        kMinOutput = min;
    		m_hopperMotor.configPeakOutputReverse(kMinOutput, HopperPIDs.kTimeoutMs);
      }

      //Want the Index to be updated every 20 milliseconds
      SmartDashboard.putNumber("Daisy Index", daisyIndex);
    }
  }
  

  /**
   *  Move the daisy one full rotation, from current position
   */
  public void dischargeAll(){
    // m_setpoint = m_hopperMotor.getSelectedSensorPosition() + HopperConstants.ONE_ROTATION;
    daisyIndex += 6;
    m_setpoint = daisyIndex * HopperConstants.ONE_ROTATION * 60 / 360 + HopperConstants.DAISY_OFFSET;
    if( HopperPIDs.TUNE) {
      System.out.println("HopperSubsystem Setting the hopper setpoint to " + m_setpoint);
    }
    m_hopperMotor.set(ControlMode.Position, m_setpoint); 
  }

  /**
   *  Move the daisy 1/6 rotation forward, from current position
   */
  public void nextSlot(){
    // m_setpoint = m_hopperMotor.getSelectedSensorPosition() + HopperConstants.ONE_ROTATION / 6.0;
    // daisyIndex = (daisyIndex+1) % 6;
    daisyIndex++;
    m_setpoint = daisyIndex * HopperConstants.ONE_ROTATION * 60 / 360 + HopperConstants.DAISY_OFFSET;
    m_hopperMotor.set(ControlMode.Position, m_setpoint);
    if( HopperPIDs.TUNE) {
      SmartDashboard.putNumber("DAISY MOVES ONE SIXTH ROTATION, to index", daisyIndex);
    }    
    if(daisyIndex < 6) {
      daisyIndex ++;
    }
    else{
      daisyIndex = 0;
    }
    
  }

  /**
   *  Move the daisy 1/6 rotation backward, from current position
   */
  public void previousSlot() {
    daisyIndex++;
    m_setpoint = daisyIndex * HopperConstants.ONE_ROTATION * 60 / 360 + HopperConstants.DAISY_OFFSET;
    /*
    m_setpoint = m_hopperMotor.getSelectedSensorPosition() - HopperConstants.ONE_ROTATION / 6.0;
 
    if(daisyIndex > 0) {
      daisyIndex --;
    }
    else{
      daisyIndex = 6;
    }
    */
    m_hopperMotor.set(ControlMode.Position, m_setpoint);

    if( HopperPIDs.TUNE) {
      SmartDashboard.putNumber("DAISY MOVES ONE SIXTH ROTATION BACKWARD, to index", daisyIndex);
    if(daisyIndex > 0) {
      daisyIndex --;
    }
    else{
      daisyIndex = 6;
    }
  }
  }

  public void moveForwardSlowly() {
    m_hopperMotor.set(ControlMode.PercentOutput, .2);
    if( HopperPIDs.TUNE) {
      SmartDashboard.putString("MOVING DAISY SLOWLY", "shooooooooosh!!");
    }
  }
  // stop
  public void stopMotor() {
    m_hopperMotor.set(ControlMode.PercentOutput, 0);
  }

  public boolean atSetpoint(double thresholdPercent) {
    return Math.abs( m_hopperMotor.getClosedLoopError()) < HopperConstants.ONE_ROTATION * thresholdPercent;
    // return Math.abs(m_setpoint - m_hopperMotor.getSensorCollection().getPulseWidthPosition()) <= Math.abs(m_setpoint*thresholdPercent);
  }

  public void smartDashIndex()
  {
    SmartDashboard.putNumber("Daisy Index.IndexMod6", daisyIndex + (daisyIndex%6)/10.0);
  }

  /**
   * keep the most recent (HopperConstants.PHOTO_NUM_SAMPLES) photodiode samples
   */
  public void photodiodeMovingWindow() {
    photoSamples[ photoSamplesI] = m_lightSensor.getVoltage();
    photoSamplesI++;
    photoSamplesI = photoSamplesI % HopperConstants.PHOTO_NUM_SAMPLES;
  }
  /** 
   * compute the average of photodiode sensor voltages as kept in the moving average.
  */
  public void avePhotodiode() { 
    double s = 0;
    for( int i=0; i<HopperConstants.PHOTO_NUM_SAMPLES; i++) {
      s += photoSamples[ i];
    }
    
    if( s / HopperConstants.PHOTO_NUM_SAMPLES < HopperConstants.DARK_THRESH) { 
      photoIsDark = true; 
    }
    else { 
      photoIsDark = false;
    }
  }
    
  /**
   * get whether the photodiode's average is dark
   * @return true if the average value of the photodiode over recent samples is less than the threshold (HopperConstants.DARK_THRESH)
   */
  public boolean photoDiodeAveIsDark() {
    avePhotodiode();
    return photoIsDark;
  }

  /**
   * returns the position (index) of Daisy, in the range [0..5]
   */
  public int getIndexMod6() {
    return daisyIndex % 6;
  }

  /**
   * returns true if we have indexed 5 balls
   */
  public boolean daisyIsFull() {
    return (daisyIndex % 6) >= 5;
  }
    
}
