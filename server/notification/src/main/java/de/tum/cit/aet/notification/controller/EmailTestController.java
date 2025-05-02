package de.tum.cit.aet.notification.controller;

import de.tum.cit.aet.notification.service.EmailService;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for testing email functionality. This is for testing purposes only. */
@RestController
@RequestMapping("/api/test/email")
@Log4j2
public class EmailTestController {

  private final EmailService emailService;

  @Value("${notification.email.dev-mode}")
  private boolean devMode;

  public EmailTestController(EmailService emailService) {
    this.emailService = emailService;
  }

  /**
   * Endpoint to send a test email.
   *
   * @param to Recipient email address
   * @param subject Email subject
   * @param body Email body (HTML)
   * @return Response indicating success or failure
   */
  @PostMapping("/send")
  public ResponseEntity<Map<String, Object>> sendTestEmail(
      @RequestParam String to,
      @RequestParam String subject,
      @RequestParam(
              required = false,
              defaultValue =
                  "<h1>Test Email</h1><p>This is a test email from Helios notification"
                      + " service.</p>")
          String body) {

    log.info("Sending test email to: {}", to);

    boolean success = emailService.sendEmail(to, subject, body);

    if (success) {
      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "message",
              "Test email sent successfully" + (devMode ? " (DEV MODE - check logs)" : ""),
              "to",
              to,
              "subject",
              subject));
    } else {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "message", "Failed to send test email", "to", to));
    }
  }

  /**
   * Endpoint to view emails sent in development mode.
   *
   * @return List of sent emails
   */
  @GetMapping("/history")
  public ResponseEntity<List<Map<String, Object>>> getEmailHistory() {
    if (!devMode) {
      return ResponseEntity.badRequest()
          .body(List.of(Map.of("error", "This endpoint is only available in development mode")));
    }

    List<Map<String, Object>> sentEmails = emailService.getSentEmails();
    return ResponseEntity.ok(sentEmails);
  }
}
