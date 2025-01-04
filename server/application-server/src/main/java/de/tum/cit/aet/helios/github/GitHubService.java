package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentApiResponse;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentDto;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@Transactional
public class GitHubService {
  private final GitHub github;

  private final GitHubConfig gitHubConfig;

  private final ObjectMapper objectMapper;

  private final OkHttpClient okHttpClient;

  @Value("${github.authToken}")
  private String ghAuthToken;

  // Request builder for GitHub API calls with authorization header
  private Builder requestBuilder;

  private GHOrganization gitHubOrganization;

  @Value("${keycloak.tokenExchangeUrl}")
  private String tokenExchangeUrl;

  public GitHubService(
      GitHub github,
      GitHubConfig gitHubConfig,
      ObjectMapper objectMapper,
      OkHttpClient okHttpClient) {
    this.github = github;
    this.gitHubConfig = gitHubConfig;
    this.objectMapper = objectMapper;
    this.okHttpClient = okHttpClient;
  }

  @PostConstruct
  public void init() {
    this.requestBuilder =
        new Request.Builder()
            .header("Authorization", "token " + ghAuthToken)
            .header("Accept", "application/vnd.github+json");
  }

  /**
   * Retrieves the GitHub organization client.
   *
   * @return the GitHub organization client
   * @throws IOException if an I/O error occurs
   */
  public GHOrganization getOrganizationClient() throws IOException {
    if (gitHubOrganization == null) {
      final String organizationName = gitHubConfig.getOrganizationName();
      if (organizationName == null || organizationName.isEmpty()) {
        log.error(
            "No organization name provided in the configuration. GitHub organization client will"
                + " not be initialized.");
        throw new RuntimeException("No organization name provided in the configuration.");
      }
      gitHubOrganization = github.getOrganization(organizationName);
    }
    return gitHubOrganization;
  }

  /**
   * Retrieves the GitHub repository.
   *
   * @param repoNameWithOwners the repository name with owners
   * @return the GitHub repository
   * @throws IOException if an I/O error occurs
   */
  public GHRepository getRepository(String repoNameWithOwners) throws IOException {
    return github.getRepository(repoNameWithOwners);
  }

  /**
   * Retrieves the list of workflows for a given repository.
   *
   * @param repoNameWithOwners the repository name with owners
   * @return the list of workflows
   * @throws IOException if an I/O error occurs
   */
  public List<GHWorkflow> getWorkflows(String repoNameWithOwners) throws IOException {
    return getRepository(repoNameWithOwners).listWorkflows().toList();
  }

  /**
   * Retrieves a specific workflow for a given repository.
   *
   * @param repoNameWithOwners the repository name with owners
   * @param workflowFileNameOrId the workflow file name or ID
   * @return the GitHub workflow
   * @throws IOException if an I/O error occurs
   */
  public GHWorkflow getWorkflow(String repoNameWithOwners, String workflowFileNameOrId)
      throws IOException {
    return getRepository(repoNameWithOwners).getWorkflow(workflowFileNameOrId);
  }

  /**
   * Dispatches a workflow for a given repository.
   *
   * @param repoNameWithOwners the repository name with owners
   * @param workflowFileNameOrId the workflow file name or ID
   * @param ref the reference (branch or tag) to run the workflow on
   * @param inputs the inputs for the workflow
   * @throws IOException if an I/O error occurs
   */
  public void dispatchWorkflow(
      String repoNameWithOwners,
      String workflowFileNameOrId,
      String ref,
      Map<String, Object> inputs,
      String token)
      throws IOException {
    // String gitHubToken = exchangeGitHubToken(token);
    String gitHubToken = ghAuthToken;
    final String url =
        String.format(
            "https://api.github.com/repos/%s/actions/workflows/%s/dispatches",
            repoNameWithOwners, workflowFileNameOrId);

    var payload = Map.of("ref", ref, "inputs", inputs);
    String jsonPayload = objectMapper.writeValueAsString(payload);

    RequestBody requestBody =
        RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

    Request request =
        new Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "token " + gitHubToken)
            .header("Accept", "application/vnd.github+json")
            .build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }
    } catch (IOException e) {
      log.error("Error occurred while dispatching workflow: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Retrieves environments from a GitHub repository and maps them to the GitHubEnvironmentDto.
   *
   * @param repository the GitHub repository as a GHRepository object
   * @return a list of GitHubEnvironmentDto objects
   * @throws IOException if an I/O error occurs
   */
  public List<GitHubEnvironmentDto> getEnvironments(GHRepository repository) throws IOException {
    final String owner = repository.getOwnerName();
    final String repoName = repository.getName();
    final String url =
        String.format("https://api.github.com/repos/%s/%s/environments", owner, repoName);

    Request request = requestBuilder.url(url).build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }

      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      String responseBody = response.body().string();
      GitHubEnvironmentApiResponse envResponse =
          objectMapper.readValue(responseBody, GitHubEnvironmentApiResponse.class);
      return envResponse.getEnvironments();
    } catch (JsonProcessingException e) {
      log.error("Error processing JSON response: {}", e.getMessage());
      throw e;
    } catch (IOException e) {
      log.error("Error occurred while fetching environments: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Retrieves a GitHub deployment iterator for a given repository and environment.
   *
   * @param repository the GitHub repository as a GHRepository object
   * @param environmentName the environment name
   * @return a GitHubDeploymentIterator object
   */
  public Iterator<GitHubDeploymentDto> getDeploymentIterator(
      GHRepository repository, String environmentName) {
    return new GitHubDeploymentIterator(
        repository, environmentName, okHttpClient, requestBuilder, objectMapper);
  }

  public GHPermissionType getPermissionForRepository(Long repoId, String keycloakToken) {
    try {
      String username = getUsernameFromToken(keycloakToken);
      GHRepository repository = github.getRepositoryById(repoId);
      return repository.getPermission(username);
    } catch (IOException e) {
      log.error("Error occurred while fetching repository permissions: {}", e.getMessage());
      return GHPermissionType.NONE;
    } catch (IllegalArgumentException e) {
      log.error("JWT token does not contain preferred_username claim: {}", e.getMessage());
      return GHPermissionType.NONE;
    }
  }

  /**
   * Exchanges JWT token from keycloak for github identity provider token.
   *
   * @param JWT keycloakToken
   * @return GitHub token
   * @throws RuntimeException GitHub token exchange failed
   */
  private String exchangeGitHubToken(String keycloakToken) {
    OkHttpClient client = new OkHttpClient().newBuilder().build();
    Request request =
        new Request.Builder()
            .url(tokenExchangeUrl)
            .method("GET", null)
            .addHeader("Authorization", keycloakToken)
            .build();
    try {
      Response response = client.newCall(request).execute();
      String responseBody = response.body().string();
      return responseBody.split("&")[0].split("=")[1];
    } catch (IOException e) {
      log.error("Error occurred while exchanging GitHub token: {}", e.getMessage());
      throw new RuntimeException("GitHub token exchange failed", e);
    }
  }

  private String getUsernameFromToken(String bearerToken) throws IOException {

    String token = bearerToken.replace("Bearer", "").trim();
    String[] jwtParts = token.split("\\.");
    if (jwtParts.length < 2) {
      throw new IllegalArgumentException("Invalid JWT token format.");
    }
    String base64Payload = jwtParts[1];
    byte[] decodedBytes = Base64.getUrlDecoder().decode(base64Payload);
    String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);

    ObjectMapper payloadObjectMapper = new ObjectMapper();
    JsonNode jsonNode = payloadObjectMapper.readTree(payloadJson);
    return jsonNode.get("preferred_username").asText();
  }
}
