package de.tum.cit.aet.helios.deploymentprotection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.GitHubClientManager;
import de.tum.cit.aet.helios.github.GitHubCustomMessageHandler;
import io.nats.client.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubDeploymentProtectionRuleMessageHandler
    implements GitHubCustomMessageHandler<DeploymentProtectionRulePayload> {

  private final GitHubClientManager clientManager;

  private final OkHttpClient okHttpClient;

  private final ObjectMapper objectMapper;

  public GitHubDeploymentProtectionRuleMessageHandler(
      GitHubClientManager clientManager,
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper) {
    this.clientManager = clientManager;
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handleMessage(Message msg) {
    if (clientManager.getAuthType().equals(GitHubClientManager.AuthType.PAT)) {
      log.warn("Received deployment_protection_rule event but no GitHub App is configured.");
      return;
    }


    String rawJson = new String(msg.getData(), StandardCharsets.UTF_8);
    log.info("Received deployment_protection_rule event. Started processing...");
    try {
      DeploymentProtectionRulePayload eventPayload =
          objectMapper.readValue(rawJson, DeploymentProtectionRulePayload.class);

      log.info(
          "Received deployment protection rule event for "
              + "repository: {}, "
              + "environment: {}, "
              + "sender: {}, "
              + "action: {}",
          eventPayload.getRepository().getFullName(),
          eventPayload.getEnvironment(),
          eventPayload.getSender().getLogin(),
          eventPayload.getAction());

      if (isValidDeploymentRequest(eventPayload)) {
        log.info("Deployment protection rule accepted. Processing...");
        sendApprovalRequest(eventPayload, "approved");
      } else {
        log.info("Deployment protection rule rejected. Ignoring...");
        sendApprovalRequest(eventPayload, "rejected");
      }

    } catch (IOException e) {
      log.error("Failed to parse deployment_protection_rule payload", e);
    } catch (Exception e) {
      log.error("Failed to process deployment_protection_rule payload", e);
    }
  }

  /**
   * Checks if the deployment is triggered by the GitHub App.
   *
   * @param eventPayload The deployment protection rule event payload.
   * @return True if the deployment request is valid, false otherwise.
   */
  private boolean isValidDeploymentRequest(DeploymentProtectionRulePayload eventPayload) {
    return "requested".equals(eventPayload.getAction())
        && eventPayload.getSender().getNodeId()
        .equalsIgnoreCase(clientManager.getGithubAppNodeId());
  }


  /**
   * Sends the approval request to the GitHub API.
   *
   * @param eventPayload The deployment protection rule event payload.
   * @param state        The desired state of the deployment (either "approved" or "rejected").
   */
  private void sendApprovalRequest(DeploymentProtectionRulePayload eventPayload, String state) {
    RequestBody body = createRequestBody(eventPayload, state);

    if (body == null) {
      log.error("Failed to create request body for deployment protection rule.");
      return;
    }

    Request request = buildRequest(eventPayload.getDeploymentCallbackUrl(), body);

    try (Response response = okHttpClient.newCall(request).execute()) {
      handleResponse(response, eventPayload, state);
    } catch (IOException e) {
      log.error("Error sending {} request for deployment protection rule.", state, e);
    }
  }

  /**
   * Creates the request body to send to the GitHub API.
   *
   * @param eventPayload The deployment protection rule event payload.
   * @param state        The desired state of the deployment.
   * @return The created request body.
   */
  private RequestBody createRequestBody(
      DeploymentProtectionRulePayload eventPayload,
      String state) {
    try {
      return RequestBody.create(
          objectMapper.writeValueAsString(Map.of(
              "environment_name", eventPayload.getEnvironment(),
              "state", state,
              "comment", String.format("Deployment protection rule %s by Helios.", state)
          )),
          MediaType.get("application/json; charset=utf-8")
      );
    } catch (JsonProcessingException e) {
      log.error("Failed to create request body with state: {}", state, e);
      return null;
    }
  }

  /**
   * Builds the request to send to the GitHub API.
   *
   * @param url  The URL to send the request to.
   * @param body The request body.
   * @return The built request.
   */
  private Request buildRequest(String url, RequestBody body) {
    return getRequestBuilder()
        .url(url)
        .post(body)
        .build();
  }

  /**
   * Handles the response from the GitHub API.
   *
   * @param response     The response from the GitHub API.
   * @param eventPayload The deployment protection rule event payload.
   * @param state        The state of the deployment protection rule.
   * @throws IOException If an I/O error occurs.
   */
  private void handleResponse(Response response, DeploymentProtectionRulePayload eventPayload,
                              String state) throws IOException {
    if (response.isSuccessful()) {
      log.info("Successfully sent {} request for deployment protection rule.", state);
    } else if (response.code() == 422) {
      handleUnprocessableEntity(response, eventPayload, state);
    } else {
      log.error(
          "Failed to send {} request for deployment protection rule. "
              + "Response code: {}, Response body: {}",
          state, response.code(), response.body() != null ? response.body().string() : "null"
      );
    }
  }

  /**
   * Handles the 422 response from the GitHub API.
   *
   * @param response     The response from the GitHub API.
   * @param eventPayload The deployment protection rule event payload.
   * @param state        The state of the deployment protection rule.
   * @throws IOException If an I/O error occurs.
   */
  private void handleUnprocessableEntity(
      Response response,
      DeploymentProtectionRulePayload eventPayload,
      String state) throws IOException {
    String responseBody = response.body() != null ? response.body().string() : "null";
    try {
      String workflowRunId = extractWorkflowRunId(eventPayload.getDeploymentCallbackUrl());
      System.out.println("workflowRunId: " + workflowRunId);
      String noPendingRequestsMessage =
          "No pending custom deployment requests in workflow run "
              + "`" + workflowRunId + "` to approve or reject";

      Map<String, String> responseJson =
          objectMapper.readValue(responseBody, new TypeReference<>() {
          });
      String message = responseJson.get("message");

      if (noPendingRequestsMessage.equalsIgnoreCase(message)) {
        log.warn("No pending custom deployment requests in workflow run `{}` to approve or reject",
            workflowRunId);
      } else {
        log.error(
            "Failed to send {} request for deployment protection rule. "
                + "Response code: 422, Response body: {}",
            state, responseBody);
      }
    } catch (Exception e) {
      log.error("Failed to process 422 response. Response code: 422, Response body: {}",
          responseBody, e);
    }
  }

  /**
   * Extracts the workflow run ID from the deployment callback URL.
   *
   * @param callbackUrl The deployment callback URL.
   * @return The extracted workflow run ID.
   */
  private String extractWorkflowRunId(String callbackUrl) {
    try {
      // URL Format
      // https://api.github.com/repos/:owner/:repo/actions/runs/:run_id/deployment_protection_rule
      return callbackUrl.split("/")[8];
    } catch (ArrayIndexOutOfBoundsException e) {
      log.warn("Failed to extract workflow run ID from callback URL: {}", callbackUrl, e);
      return "";
    }
  }

  public Request.Builder getRequestBuilder() {
    return new Request.Builder()
        .header("Authorization", "token " + clientManager.getCurrentToken())
        .header("Accept", "application/vnd.github+json");
  }

  @Override
  public String getEventType() {
    return "deployment_protection_rule";
  }
}
