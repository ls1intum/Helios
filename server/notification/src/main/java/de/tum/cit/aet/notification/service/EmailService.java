package de.tum.cit.aet.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;
  private final EmailTemplateService templateService;

  @Value("${notification.email.enabled}")
  private boolean emailEnabled;

  @Value("${notification.email.from}")
  private String fromEmail;

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
    log.info("Sending templated email to: {}", to);

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

}
