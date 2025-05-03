package de.tum.cit.aet.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for health check endpoint.
 *
 * <p>This controller provides a simple health check endpoint to verify the status of the service.
 * It returns a 200 OK response with a simple "ok" message.
 */
@RestController
@RequestMapping("/status")
public class StatusController {

  /**
   * Health check endpoint.
   *
   * <p>This endpoint returns a 200 OK response with a simple "ok" message. It can be used to check
   * the health of the service.
   *
   * @return "ok" message indicating the service is healthy
   */
  @GetMapping("/health")
  @ResponseStatus(HttpStatus.OK)
  public String healthCheck() {
    return "ok";
  }
}
