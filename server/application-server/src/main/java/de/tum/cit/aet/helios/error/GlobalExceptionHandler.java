package de.tum.cit.aet.helios.error;

import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.environment.EnvironmentException;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateException;
import de.tum.cit.aet.helios.tests.type.TestTypeNameConflictException;
import io.sentry.Sentry;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

  // -- 400 BAD REQUEST : validation & deserialization -----------------
  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ApiError> handleValidationErrors(Exception ex, HttpServletRequest request) {

    // Collect field‑level messages if present
    String message;
    if (ex instanceof MethodArgumentNotValidException manve) {
      message =
          manve.getBindingResult().getFieldErrors().stream()
              .map(fe -> "%s %s".formatted(fe.getField(), fe.getDefaultMessage()))
              .collect(Collectors.joining("; "));
    } else if (ex instanceof BindException be) {
      message =
          be.getFieldErrors().stream()
              .map(fe -> "%s %s".formatted(fe.getField(), fe.getDefaultMessage()))
              .collect(Collectors.joining("; "));
    } else {
      message = ex.getMessage();
    }

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
    error.setMessage("Validation failed: " + message);
    error.setPath(request.getRequestURI());
    error.setTimestamp(java.time.Instant.now());

    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  /** Empty body / bad JSON (e.g. missing closing brace) */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(Exception ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.BAD_REQUEST.value());
    error.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
    error.setMessage("Request body is missing or malformed");
    error.setPath(request.getRequestURI());
    error.setTimestamp(java.time.Instant.now());

    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

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
    log.error("An internal server error occurred", ex);

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(DeploymentException.class)
  public ResponseEntity<ApiError> handleDeploymentException(
      DeploymentException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError("Deployment Error");

    // Check if this is a GitHub API error
    String message = ex.getMessage();
    if (message != null && message.contains("GitHub API error:")) {
      error.setMessage(message);

      // For validation errors, use BAD_REQUEST status
      if (message.contains("Required input") || message.contains("Validation failed")) {
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("GitHub Validation Error");
      }
    } else {
      error.setMessage("Deployment failed: " + message);
    }

    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.valueOf(error.getStatus()));
  }

  @ExceptionHandler(ReleaseCandidateException.class)
  public ResponseEntity<ApiError> handleReleaseCandidateException(
      ReleaseCandidateException ex, HttpServletRequest request) {

    ApiError error = new ApiError();
    error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.setError("Release Candidate Error");
    error.setMessage("Release Candidate operation failed: " + ex.getMessage());
    error.setPath(request.getRequestURI());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(TestTypeNameConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ResponseEntity<ApiError> handleTestTypeNameConflict(TestTypeNameConflictException ex) {
    ApiError error = new ApiError();
    error.setStatus(HttpStatus.CONFLICT.value());
    error.setError("Test Type Name Conflict");
    error.setMessage("Test type name conflict: " + ex.getMessage());
    error.setTimestamp(Instant.now());

    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
  }
}
