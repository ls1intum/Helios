package de.tum.cit.aet.helios.github.permissions;

public class PermissionException extends RuntimeException {
  public PermissionException(String message) {
    super(message);
  }

  public PermissionException(String message, Throwable cause) {
    super(message, cause);
  }
}
