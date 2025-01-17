package de.tum.cit.aet.helios.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiError> handleEntityNotFoundException(
      EntityNotFoundException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.NOT_FOUND.value());
    error.setError(HttpStatus.NOT_FOUND.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleIllegalStateException(
      IllegalStateException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiError> handleSecurityException(
      SecurityException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.FORBIDDEN.value());
    error.setError(HttpStatus.FORBIDDEN.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
  }

  // A fallback for any uncaught exceptions
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneralException(
      Exception ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
