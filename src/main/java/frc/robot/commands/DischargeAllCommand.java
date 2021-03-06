/*----------------------------------------------------------------------------*/
/* Copyright (c) 2019 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.subsystems.HopperSubsystem;

public class DischargeAllCommand extends CommandBase {
  HopperSubsystem m_subsystem;
  
  /**
   * Creates a new DischargeAll.
   */
  public DischargeAllCommand(HopperSubsystem hopper) {
    // Use addRequirements() here to declare subsystem dependencies.
    addRequirements(hopper);

    m_subsystem = hopper;
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    m_subsystem.dischargeAll();
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
    // Wait for the setpoint to be reached
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    // No need to do anything -- the subsystem holds itself in PID
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return m_subsystem.atSetpoint(5);
  }
}
