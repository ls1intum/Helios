package de.tum.cit.aet.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service for sending emails. In development mode, emails are logged instead of actually being
 * sent.
 */
@Service
@Log4j2
public class EmailService {

  private final JavaMailSender mailSender;
  private final EmailTemplateService templateService;

  @Value("${notification.email.enabled}")
  private boolean emailEnabled;

  @Value("${notification.email.dev-mode}")
  private boolean devMode;

  @Value("${notification.email.from}")
  private String fromEmail;

  // For storing emails in dev mode
  private final LinkedList<Map<String, Object>> sentEmails = new LinkedList<>();
  private static final int MAX_STORED_EMAILS = 50;

  @Autowired
  public EmailService(JavaMailSender mailSender, EmailTemplateService templateService) {
    this.mailSender = mailSender;
    this.templateService = templateService;
  }

  /**
   * Sends an email using a template.
   *
   * @param to recipient email address
   * @param templateName name of the template to use (without .html extension)
   * @param parameters parameters to substitute in the template
   * @param subject email subject
   * @return true if the email was sent successfully
   */
  public boolean sendTemplatedEmail(
      String to, String templateName, Map<String, Object> parameters, String subject) {
    if (!emailEnabled) {
      log.info("Email sending is disabled. Skipping templated email to: {}", to);
      return false;
    }

    try {
      // Process the template with parameters
      String body = templateService.processTemplate(templateName, parameters);

      // Send the email with the processed body
      return sendEmail(to, subject, body);
    } catch (Exception e) {
      log.error("Failed to process template or send email to: {}", to, e);
      return false;
    }
  }

  /**
   * Sends an email. In dev mode, the email is logged instead of being sent.
   *
   * @param to recipient email address
   * @param subject email subject
   * @param body email body (HTML)
   * @return true if the email was sent successfully
   */
  public boolean sendEmail(String to, String subject, String body) {
    if (!emailEnabled) {
      log.info("Email sending is disabled. Skipping email to: {}", to);
      return false;
    }

    // Store email information in development mode
    if (devMode) {
      storeEmail(to, subject, body);
      log.info("DEV MODE: Email would be sent to: {}", to);
      log.info("Subject: {}", subject);
      log.info("Body: {}", body);
      return true;
    }

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromEmail);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, true); // true indicates HTML content

      mailSender.send(message);
      log.info("Email sent successfully to: {}", to);
      return true;
    } catch (MessagingException e) {
      log.error("Failed to send email to: {}", to, e);
      return false;
    }
  }

  /**
   * Stores email information in memory for development mode.
   *
   * @param to recipient email address
   * @param subject email subject
   * @param body email body
   */
  private void storeEmail(String to, String subject, String body) {
    synchronized (sentEmails) {
      Map<String, Object> emailDetails = new HashMap<>();
      emailDetails.put("to", to);
      emailDetails.put("subject", subject);
      emailDetails.put("body", body);
      emailDetails.put("timestamp", System.currentTimeMillis());

      sentEmails.addFirst(emailDetails);

      // Limit the size of stored emails
      while (sentEmails.size() > MAX_STORED_EMAILS) {
        sentEmails.removeLast();
      }
    }
  }

  /**
   * Gets the list of emails sent in development mode.
   *
   * @return list of sent email details
   */
  public List<Map<String, Object>> getSentEmails() {
    if (!devMode) {
      log.warn("Attempted to retrieve sent emails while not in dev mode");
      return Collections.emptyList();
    }

    synchronized (sentEmails) {
      return new ArrayList<>(sentEmails);
    }
  }
}
