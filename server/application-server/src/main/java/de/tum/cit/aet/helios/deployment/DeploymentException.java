package de.tum.cit.aet.helios.deployment;

public class DeploymentException extends RuntimeException {
  public DeploymentException(String message) {
    super(message);
  }

  public DeploymentException(String message, Throwable cause) {
    super(message, cause);
  }
}
