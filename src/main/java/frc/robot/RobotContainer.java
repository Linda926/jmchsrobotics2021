/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018-2019 FIRST. All Rights Reserved.                        */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot;

import com.revrobotics.ColorSensorV3;

import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.commands.DefaultSwerveCommand;
import frc.robot.commands.SampleColorCommand;
import frc.robot.commands.SendVisionCommand;
import frc.robot.commands.SetThrowerSpeedCommand;
import frc.robot.commands.VisionApproachTarget;
import frc.robot.commands.VisionLineUpWithTarget;
import frc.robot.subsystems.SwerveDriveSubsystem;
import frc.robot.subsystems.ThrowerSubsystem;
import frc.robot.util.SocketVisionSendWrapper;
import frc.robot.util.SocketVisionWrapper;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.StartEndCommand;;

/**
 * This class is where the bulk of the robot should be declared. Since
 * Command-based is a "declarative" paradigm, very little robot logic should
 * actually be handled in the {@link Robot} periodic methods (other than the
 * scheduler calls). Instead, the structure of the robot (including subsystems,
 * commands, and button mappings) should be declared here.
 */
public class RobotContainer {
  // Define subsystems here
  private final SwerveDriveSubsystem m_swerve = new SwerveDriveSubsystem();
  private final ThrowerSubsystem m_Thrower = new ThrowerSubsystem();

  // Vision objects
  private final SocketVisionWrapper rft_ = new SocketVisionWrapper("10.59.33.255", 5801);
  private final SocketVisionWrapper piece_ = new SocketVisionWrapper("10.59.33.255", 5805);
  private final SocketVisionSendWrapper sender_ = new SocketVisionSendWrapper("10.59.33.255", 5800);

  // Color Sensor
  private final ColorSensorV3 m_colorSensor = new ColorSensorV3(I2C.Port.kOnboard);
  private final AnalogInput m_lightSensor = new AnalogInput(0);
  // DriveStation for GameSpecificMessage
  DriverStation m_station = DriverStation.getInstance();

  // Define Joysticks and Buttons here
  private final XboxController m_primaryController = new XboxController(0);
  private final XboxController m_secondaryController = new XboxController(1);

  private final JoystickButton m_primaryController_A = new JoystickButton(m_primaryController,
      XboxController.Button.kA.value);
  private final JoystickButton m_primaryController_B = new JoystickButton(m_primaryController,
      XboxController.Button.kB.value);
  private final JoystickButton m_primaryController_LeftBumper = new JoystickButton(m_primaryController, 
      XboxController.Button.kBumperLeft.value);
  private final JoystickButton m_primaryController_RightBumper = new JoystickButton(m_primaryController, 
      XboxController.Button.kBumperRight.value);

  private final JoystickButton m_secondaryController_StickLeft = new JoystickButton(m_secondaryController,
      XboxController.Button.kStickLeft.value);
  private final JoystickButton m_secondaryController_B = new JoystickButton(m_secondaryController, 
      XboxController.Button.kB.value);
  private final JoystickButton m_secondaryController_A = new JoystickButton(m_secondaryController, 
      XboxController.Button.kA.value);
  private final JoystickButton m_secondaryController_Y = new JoystickButton(m_secondaryController, 
      XboxController.Button.kY.value);
  private final JoystickButton m_secondaryController_X = new JoystickButton(m_secondaryController, 
      XboxController.Button.kX.value);

  /**
   * The container for the robot. Contains subsystems, OI devices, and commands.
   */
  public RobotContainer() {
    // Configure the default commands
    configureDefaultCommands();
    // Configure the button bindings
    configureButtonBindings();
  }

  /**
   * Use this method to define your button->command mappings. Buttons can be
   * created by instantiating a {@link GenericHID} or one of its subclasses
   * ({@link edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then
   * passing it to a {@link edu.wpi.first.wpilibj2.command.button.JoystickButton}.
   */
  private void configureButtonBindings() {
    m_primaryController_A.whenPressed(      
      new SequentialCommandGroup(
        new InstantCommand(m_swerve::setBrakeOn, m_swerve), // Brake mode on!
        new SendVisionCommand(sender_, "R"), // Can't be a lambda because Sender's aren't subsystems
        new VisionLineUpWithTarget(m_swerve, rft_), 
        new SendVisionCommand(sender_, "_")
      )
    );

    m_primaryController_B.whenPressed( // Inline command group!
      new SequentialCommandGroup(
        new InstantCommand(m_swerve::setBrakeOn, m_swerve), // Brake mode on!
        new SendVisionCommand(sender_, "G"), // Can't be a lambda because Sender's aren't subsystems
        new VisionLineUpWithTarget(m_swerve, piece_), 
        new SendVisionCommand(sender_, "_")
      )
    );

    //left bumper is field orientated // has a fun lambda
    m_primaryController_LeftBumper.whenPressed(
        new InstantCommand(()-> {m_swerve.setFieldOriented(false);})
      ).whenReleased(
        new InstantCommand(()-> {m_swerve.setFieldOriented(true);})
      );

    // Put brake mode on a button!
    m_primaryController_RightBumper.whenPressed(
        new InstantCommand(m_swerve::setBrakeOn, m_swerve)
      ).whenReleased(
        new InstantCommand(m_swerve::setBrakeOff, m_swerve)
      );

    m_secondaryController_StickLeft.whileHeld(
      new SampleColorCommand(m_colorSensor)
    );

    // Thrower on secondary controller, 'B'
    m_secondaryController_B.whenHeld( // ) Pressed( // whileHeld(
      new SetThrowerSpeedCommand(m_Thrower, 700).perpetually() // m_Thrower.getThrowerSpeed())
    )
    .whenReleased(
    new InstantCommand(m_Thrower::stopThrower, m_Thrower) // Let the thrower coast to a stop. Is also the default command.
      // perhaps the button should call SetThrowerSpeed() whenHeld() instead.
    );


    //The command sequence to line up the robot with power port and shooting 5 powercells
    m_secondaryController_A.whenPressed(
      new SequentialCommandGroup(
        new ParallelCommandGroup(
          new SetThrowerSpeedCommand(m_Thrower, 5000),
          // Angle the robot toward the retroflective tape
          new SequentialCommandGroup(
            new InstantCommand(m_swerve::setBrakeOn, m_swerve), // Brake mode on!
            new SendVisionCommand(sender_, "R"), // Can't be a lambda because Sender's aren't subsystems
            new VisionLineUpWithTarget(m_swerve, rft_), 
            new SendVisionCommand(sender_, "_")
          )
        ),
        new SequentialCommandGroup(
          //Spin the daisy and reset the Ball Count to 0
          // Stop thrower
        )
      )
    );

    //The sequence for loading 5 powercells into the daisy
    m_secondaryController_X.whileHeld(
        new SequentialCommandGroup(
          //if the ball count is less than five
            new SequentialCommandGroup(
              // Lower intake command
              //spin intake forward
              // put power cell in daisy
            ),
            new ParallelCommandGroup(
              new SequentialCommandGroup(
                // move the hopper into the next slot
                // Ball count ++
              )
              //spin the intake backwards (little bit)
            ),
          //else the ball count is 5 (hopefully not higher)
            new SequentialCommandGroup(
              //raise intake
              //spin backwards
          )
        )
        
    );

    //Tells the output of the light sensor used for telling if powercell is in daisy
    m_secondaryController_Y.whileHeld(new InstantCommand(()->{SmartDashboard.putNumber("output", m_lightSensor.getVoltage());}));
  }

  private void configureDefaultCommands() {
    // Using StartEnd commands because by default they do not have isFinished return true, unlike InsantCommands. Alternative is to use the perpetually() decorator.
    // default swerve drive is to read from joysticks
    m_swerve.setDefaultCommand(new DefaultSwerveCommand(m_swerve, m_primaryController));

    // default thrower is to spin down to still
    m_Thrower.setDefaultCommand(new StartEndCommand( m_Thrower::stopThrower, ()->{}, m_Thrower)); // Spin down thrower on startup, do nothing on end.

  }

  /**
   * This grabs the game message from the Driver Station
   * @return The game specific message.
   */
  public String getGameSpecificMessage(){
    return m_station.getGameSpecificMessage();
  }

  /**
   * Call this to initialize the SocketVision objects. Must be called in periodInit() methods
   * if you want to use vision in that periodPeriodic() time (where period is autonomous or teleop).
   */
  public void visionInit(){
    sender_.init();
    rft_.init();
    piece_.init();
  }

  /**
   * Call this to shut down the SocketVision objects. Must be called in disabledInit() to reduce stray threads
   * running in disabled.
   */
  public void visionShutDown(){
    sender_.shutDown();
    rft_.shutDown();
    piece_.shutDown();
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    Command autoCommand = new VisionApproachTarget(m_swerve, rft_, 100, 5, 5);
    
    return autoCommand;
  }
}
