package de.tum.cit.aet.helios.error;

import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.environment.EnvironmentException;
import de.tum.cit.aet.helios.tag.TagException;
import io.sentry.Sentry;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  // -- 404 NOT FOUND -----------------------------------
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

  // -- 400 BAD REQUEST ----------------------------------
  // Combining IllegalStateException & IllegalArgumentException
  @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiError> handleBadRequestExceptions(
      RuntimeException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  // -- 403 FORBIDDEN ------------------------------------
  @ExceptionHandler({SecurityException.class, EnvironmentException.class})
  public ResponseEntity<ApiError> handleSecurityExceptions(
      RuntimeException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.FORBIDDEN.value());
    error.setError(HttpStatus.FORBIDDEN.getReasonPhrase());
    error.setMessage("Error: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
  }

  // -- 500 INTERNAL SERVER ERROR (FALLBACK) -------------
  @ExceptionHandler({Exception.class, IOException.class})
  public ResponseEntity<ApiError> handleGeneralException(Exception ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    error.setMessage("Error: An internal server error occurred");
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    Sentry.captureException(ex);

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(DeploymentException.class)
  public ResponseEntity<ApiError> handleDeploymentException(
      DeploymentException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError("Deployment Error");
    error.setMessage("Deployment failed: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(TagException.class)
  public ResponseEntity<ApiError> handleTagException(TagException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError("Tag Error");
    error.setMessage("Tag operation failed: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
