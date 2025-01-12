package de.tum.cit.aet.helios.deploymentprotection;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import io.nats.client.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubDeploymentProtectionRuleMessageHandler
    implements GitHubCustomMessageHandler<DeploymentProtectionRulePayload> {

  private final ObjectMapper objectMapper;

  public GitHubDeploymentProtectionRuleMessageHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handleMessage(Message msg) {
    log.debug("Received message: {}", msg);

    String rawJson = new String(msg.getData(), StandardCharsets.UTF_8);
    try {
      DeploymentProtectionRulePayload payload =
          objectMapper.readValue(rawJson, DeploymentProtectionRulePayload.class);

      log.info("Parsed deployment_protection_rule event: {}", payload);

      if ("requested".equals(payload.getAction())) {
        log.info("Processing a 'requested' action for environment: {}", payload.getEnvironment());
      }

    } catch (IOException e) {
      log.error("Failed to parse deployment_protection_rule payload", e);
    } catch (Exception e) {
      log.error("Failed to process deployment_protection_rule payload", e);
    }
  }

  @Override
  public String getEventType() {
    return "deployment_protection_rule";
  }
}
