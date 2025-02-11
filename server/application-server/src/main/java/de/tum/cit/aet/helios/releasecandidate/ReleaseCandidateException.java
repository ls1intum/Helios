package de.tum.cit.aet.helios.releasecandidate;

public class ReleaseCandidateException extends RuntimeException {
  public ReleaseCandidateException(String message) {
    super(message);
  }

  public ReleaseCandidateException(String message, Throwable cause) {
    super(message, cause);
  }
}
