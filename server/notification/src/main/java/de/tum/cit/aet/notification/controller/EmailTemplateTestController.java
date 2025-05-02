package de.tum.cit.aet.notification.controller;

import de.tum.cit.aet.notification.service.EmailService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for testing email templates functionality. */
@RestController
@RequestMapping("/api/test/email-templates")
@Log4j2
public class EmailTemplateTestController {

  private final EmailService emailService;
  private final ResourceLoader resourceLoader;

  @Value("${notification.email.dev-mode}")
  private boolean devMode;

  public EmailTemplateTestController(EmailService emailService, ResourceLoader resourceLoader) {
    this.emailService = emailService;
    this.resourceLoader = resourceLoader;
  }

  /**
   * Get a list of available email templates.
   *
   * @return List of template names
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> listTemplates() {
    try {
      Resource resource = resourceLoader.getResource("classpath:email-templates/");

      // This is a somewhat hacky way to get the files from the classpath resource
      // It works with file-based resources, but may not work in all environments (e.g., JAR files)
      Path path = Paths.get(resource.getURI());
      List<String> templates =
          Files.list(path)
              .filter(file -> !Files.isDirectory(file))
              .map(file -> file.getFileName().toString())
              .filter(name -> name.endsWith(".html"))
              .map(name -> name.replace(".html", ""))
              .collect(Collectors.toList());

      return ResponseEntity.ok(Map.of("templates", templates, "count", templates.size()));
    } catch (IOException e) {
      log.error("Failed to list email templates", e);
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Failed to list email templates: " + e.getMessage()));
    }
  }

  /**
   * Send a test email using any available template.
   *
   * @param templateName Name of the template to use
   * @param to Recipient email address
   * @param subject Email subject
   * @param parameters Parameters for the template
   * @return Response indicating success or failure
   */
  @PostMapping("/{templateName}")
  public ResponseEntity<Map<String, Object>> sendTemplatedEmail(
      @PathVariable String templateName,
      @RequestParam String to,
      @RequestParam String subject,
      @RequestBody Map<String, Object> parameters) {

    log.info("Sending templated email '{}' to: {}", templateName, to);

    boolean success = emailService.sendTemplatedEmail(to, templateName, parameters, subject);

    if (success) {
      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "message",
              "Templated email sent successfully" + (devMode ? " (DEV MODE - check logs)" : ""),
              "to",
              to,
              "template",
              templateName));
    } else {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "success",
                  false,
                  "message",
                  "Failed to send templated email",
                  "template",
                  templateName,
                  "to",
                  to));
    }
  }
}
