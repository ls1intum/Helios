package de.tum.cit.aet.notification.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/** Service for loading and processing email templates with dynamic content. */
@Service
@Log4j2
public class EmailTemplateService {

  private final ResourceLoader resourceLoader;
  private final Map<String, String> templateCache = new HashMap<>();
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  public EmailTemplateService(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Value("${helios.client_url}")
  private String heliosClientUrl;

  /**
   * Process the specified email template with the provided parameters.
   *
   * @param templateName the name of the template file (without .html extension)
   * @param parameters parameters to substitute in the template
   * @return processed HTML content
   */
  public String processTemplate(String templateName, Map<String, Object> parameters) {
    log.info("Processing email template: {}", templateName);
    String templateContent = getTemplateContent(templateName);
    log.info("Template content loaded for: {}", templateName);

    // Add standard parameters if not provided
    if (!parameters.containsKey("currentYear")) {
      parameters.put("currentYear", Year.now().getValue());
    }
    // Add environment-specific parameters
    if (!parameters.containsKey("heliosBaseUrl")) {
      parameters.put("heliosBaseUrl", heliosClientUrl);
    }

    // Process the template by replacing placeholders
    return replacePlaceholders(templateContent, parameters);
  }

  /**
   * Get the content of a template, using cache if available.
   *
   * @param templateName the name of the template file (without .html extension)
   * @return the template content
   */
  private String getTemplateContent(String templateName) {
    // Check if template is in cache
    if (templateCache.containsKey(templateName)) {
      return templateCache.get(templateName);
    }

    try {
      // Load template from resources
      Resource resource =
          resourceLoader.getResource("classpath:email-templates/" + templateName + ".html");

      if (!resource.exists()) {
        log.error("Email template not found: {}", templateName);
        throw new IllegalArgumentException("Email template not found: " + templateName);
      }

      // Read the template content
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
        String content = reader.lines().collect(Collectors.joining("\n"));

        // Cache the template
        templateCache.put(templateName, content);

        return content;
      }
    } catch (IOException e) {
      log.error("Failed to load email template: {}", templateName, e);
      throw new RuntimeException("Failed to load email template: " + templateName, e);
    }
  }

  /**
   * Replace placeholders in the template with actual values.
   *
   * @param template the template content
   * @param parameters map of parameter names to values
   * @return processed content
   */
  private String replacePlaceholders(String template, Map<String, Object> parameters) {
    log.info("Replacing placeholders in template");
    StringBuffer result = new StringBuffer();
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

    while (matcher.find()) {
      log.info("Found placeholder: {}", matcher.group(0));
      String placeholder = matcher.group(1);
      Object value = parameters.getOrDefault(placeholder, "");
      matcher.appendReplacement(result, value.toString().replace("$", "\\$"));
    }

    log.info("Appending remaining content");

    matcher.appendTail(result);
    return result.toString();
  }

  /** Invalidate the template cache, forcing templates to be reloaded from disk. */
  public void clearCache() {
    templateCache.clear();
    log.info("Email template cache cleared");
  }
}
