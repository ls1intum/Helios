package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.auth.github.GitHubAuthBroker;
import de.tum.cit.aet.helios.auth.github.TokenExchangeResponse;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentApiResponse;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentDto;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.permissions.GitHubPermissionsResponse;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.PagedIterable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@RequiredArgsConstructor
@Service
@Transactional
public class GitHubService {
  private final GitHubFacade github;
  private final GitHubConfig gitHubConfig;
  private final ObjectMapper objectMapper;
  private final OkHttpClient okHttpClient;
  private final AuthService authService;
  private final GitHubAuthBroker gitHubAuthBroker;
  private final GitHubClientManager clientManager;
  private GHOrganization gitHubOrganization;

  @Value("${helios.clientBaseUrl:http://localhost:4200}")
  private String heliosClientBaseUrl;

  public Builder getRequestBuilder() {
    return new Request.Builder()
        .header("Authorization", "token " + clientManager.getCurrentToken())
        .header("Accept", "application/vnd.github+json");
  }

  // We are caching the installed repositories to avoid making a request to GitHub for every
  // incoming event. The cache is invalidated when a new installation event happened.
  @Cacheable("installedRepositories")
  public List<String> getInstalledRepositories() throws IOException {
    log.info("Fetching installed repositories from GitHub");
    return github.getInstalledRepositoriesForGitHubApp();
  }

  @CacheEvict(value = "installedRepositories", allEntries = true)
  public void clearInstalledRepositoriesCache() {
    log.info("Clearing cache for installed repositories");
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
   * Retrieves the artifacts for a given repository.
   *
   * @param repoId the repository ID
   * @param workflowRunId the workflow run ID
   * @return the list of repositories as a PagedIterable object (not thread-safe)
   * @throws IOException if an I/O error occurs
   */
  public PagedIterable<GHArtifact> getWorkflowRunArtifacts(long repoId, long workflowRunId)
      throws IOException {
    return github.getRepositoryById(repoId).getWorkflowRun(workflowRunId).listArtifacts();
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
   * @param ref the reference (branch or releaseCandidate) to run the workflow on
   * @param inputs the inputs for the workflow
   * @throws IOException if an I/O error occurs
   */
  public void dispatchWorkflow(
      String repoNameWithOwners,
      String workflowFileNameOrId,
      String ref,
      Map<String, Object> inputs)
      throws IOException {
    final String url =
        String.format(
            "https://api.github.com/repos/%s/actions/workflows/%s/dispatches",
            repoNameWithOwners, workflowFileNameOrId);

    var payload = Map.of("ref", ref, "inputs", inputs);
    String jsonPayload = objectMapper.writeValueAsString(payload);

    RequestBody requestBody =
        RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

    Request request = getRequestBuilder().url(url).post(requestBody).build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
            "GitHub API call failed with response code: "
                + response.code()
                + " and body: "
                + response.body().string());
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

    Request request = getRequestBuilder().url(url).get().build();

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
   * @param since an optional timestamp to fetch deployments since
   * @return a GitHubDeploymentIterator object
   */
  public Iterator<GitHubDeploymentDto> getDeploymentIterator(
      GHRepository repository, String environmentName, Optional<OffsetDateTime> since) {
    return new GitHubDeploymentIterator(
        repository, environmentName, okHttpClient, getRequestBuilder(), objectMapper, since);
  }

  /**
   * Retrieves the GitHub repository role for the current user and the repository from the
   * RepositoryContext.
   *
   * @return GitHubRepositoryRoleDto containing the role information.
   * @throws IOException if there is an error fetching the repository ID or username.
   */
  public GitHubRepositoryRoleDto getRepositoryRole() throws IOException {
    String repositoryId;
    try {
      repositoryId = RepositoryContext.getRepositoryId().toString();
    } catch (Exception e) {
      log.error("Error occurred while fetching repository: {}", e.getMessage());
      throw new IOException("Failed to fetch repository ID", e);
    }
    String username;
    try {
      username = this.authService.getPreferredUsername();
    } catch (Exception e) {
      log.error("Error occurred while fetching username: {}", e.getMessage());
      throw new IOException("Failed to fetch username", e);
    }
    return getRepositoryRole(repositoryId, username);
  }

  /**
   * Retrieves the GitHub repository role for a given repository ID and username.
   *
   * @param repositoryId the ID of the repository.
   * @param username the GitHub username.
   * @return GitHubRepositoryRoleDto containing the role information.
   * @throws IOException if there is an error making the GitHub API call or processing the response.
   * @throws IllegalArgumentException if the repository ID or username is null or empty.
   */
  public GitHubRepositoryRoleDto getRepositoryRole(String repositoryId, String username)
      throws IOException, IllegalArgumentException {

    if (repositoryId == null || repositoryId.isEmpty()) {
      throw new IllegalArgumentException("Repository ID cannot be null or empty");
    }

    if (username == null || username.isEmpty()) {
      throw new IllegalArgumentException("Username cannot be null or empty");
    }
    String url =
        String.format(
            "https://api.github.com/repositories/%s/collaborators/%s/permission",
            repositoryId, username);

    Request request = getRequestBuilder().url(url).get().build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }

      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      String responseBody = response.body().string();
      GitHubPermissionsResponse permissionResponse =
          objectMapper.readValue(responseBody, GitHubPermissionsResponse.class);
      return new GitHubRepositoryRoleDto(
          RepoPermissionType.fromString(permissionResponse.getPermission()),
          permissionResponse.getRoleName());

    } catch (JsonProcessingException e) {
      log.error("Error processing JSON response: {}", e.getMessage());
      throw new IOException("Error processing JSON response", e);
    } catch (IOException e) {
      log.error("Error occurred while fetching permissions: {}", e.getMessage());
      throw new IOException("Error occurred while fetching permissions", e);
    } catch (Exception e) {
      log.error("Unexpected error occurred: {}", e.getMessage());
      throw new IOException("Unexpected error occurred", e);
    }
  }

  /**
   * Creates a successful commit status for a GitHub pull request with a link to the Helios page.
   *
   * <p>This method sets up a commit status with the following characteristics:
   *
   * <ul>
   *   <li>State: SUCCESS
   *   <li>Context: "Helios"
   *   <li>Target URL: A formatted URL to the Helios page for this specific pull request
   *   <li>Description: A message indicating what the link leads to
   * </ul>
   *
   * <p>The commit status is created for the HEAD commit of the pull request. Any IO exceptions
   * during the status creation are logged as errors.
   *
   * @param pullRequest The GitHub pull request object for which to create the commit status
   */
  public void createCommitStatusForPullRequest(GHPullRequest pullRequest) {
    final String targetUrl =
        String.format(
            "%s/repo/%d/ci-cd/pr/%d",
            heliosClientBaseUrl, pullRequest.getRepository().getId(), pullRequest.getNumber());
    final String description = "Click to view the Helios page of this pull request.";
    final String context = "Helios";
    try {
      github.createCommitStatus(
          pullRequest.getRepository().getFullName(),
          pullRequest.getHead().getSha(),
          GHCommitState.SUCCESS,
          targetUrl,
          description,
          context);
    } catch (IOException e) {
      log.error("Error occurred while creating commit status: {}", e.getMessage());
    }
  }

  /**
   * Approves GitHub pending deployments for a workflow run on behalf of user.
   *
   * @param repoNameWithOwner the repository name with owner
   * @param runId the workflow run ID
   * @param environmentId the IDs of environments to approve
   * @param githubUserLogin the GitHub user login
   * @throws IOException if an I/O error occurs during the API call
   */
  public void approveDeploymentOnBehalfOfUser(
      String repoNameWithOwner, long runId, Long environmentId, String githubUserLogin)
      throws IOException {
    // Construct the approval payload
    Map<String, Object> requestPayload =
        Map.of(
            "environment_ids", List.of(environmentId),
            "state", "approved",
            "comment", "Automatically approved by Helios");

    String jsonPayload = objectMapper.writeValueAsString(requestPayload);

    // Construct the URL
    String url =
        String.format(
            "https://api.github.com/repos/%s/actions/runs/%d/pending_deployments",
            repoNameWithOwner, runId);

    // Build the request with the GitHub token from Keycloak
    RequestBody requestBody =
        RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

    TokenExchangeResponse tokenExchangeResponse =
        this.gitHubAuthBroker.exchangeToken(githubUserLogin);
    if (tokenExchangeResponse == null) {
      log.error("Token exchange response is null");
      return;
    }
    String userGithubToken = tokenExchangeResponse.getAccessToken();

    Request request =
        new Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer " + userGithubToken)
            .header("Accept", "application/json")
            .build();

    // Execute the request
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = "No error details";
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          try {
            errorBody = responseBody.string();
          } catch (IOException e) {
            log.warn("Failed to read error response body", e);
          }
        }

        log.error(
            "GitHub API call failed with response code: {} and body: {}",
            response.code(),
            errorBody);
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }

      log.info("Successfully approved deployment for run ID: {}", runId);
    } catch (IOException e) {
      log.error("Error occurred while approving deployment: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Generates release notes for a GitHub repository by comparing changes between versions.
   *
   * @param repositoryNameWithOwner The repository name including the owner (e.g., "owner/repo")
   * @param tagName The tag name for the new release
   * @param targetCommitish The commitish value that determines where the Git tag is created from
   * @return The generated release notes as a string
   * @throws IOException If there's an error communicating with the GitHub API
   */
  public String generateReleaseNotes(
      String repositoryNameWithOwner, String tagName, String targetCommitish) throws IOException {

    // Create the request payload with only non-null fields
    Map<String, String> requestPayload = new HashMap<>();
    requestPayload.put("tag_name", tagName);

    if (targetCommitish != null && !targetCommitish.isEmpty()) {
      requestPayload.put("target_commitish", targetCommitish);
    }

    String jsonPayload = objectMapper.writeValueAsString(requestPayload);
    log.info("Request payload: {}", jsonPayload);

    // Build the request
    Request request =
        getRequestBuilder()
            .url(
                "https://api.github.com/repos/"
                    + repositoryNameWithOwner
                    + "/releases/generate-notes")
            .post(
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload))
            .build();

    // Execute the request
    try (Response response = okHttpClient.newCall(request).execute()) {
      ResponseBody responseBody = response.body();
      if (!response.isSuccessful()) {
        String errorBody = "No error details";
        if (responseBody != null) {
          try {
            errorBody = responseBody.string();
          } catch (IOException e) {
            log.warn("Failed to read error response body", e);
          }
        }

        log.error(
            "GitHub API call failed with response code: {} and body: {}",
            response.code(),
            errorBody);
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }

      if (responseBody == null) {
        throw new IOException("Response body is null");
      }

      // Parse the response
      Map<String, Object> responseMap = objectMapper.readValue(responseBody.string(), Map.class);

      // Return the generated notes
      log.debug("Successfully generated release notes using the GitHub API");
      return (String) responseMap.get("body");
    }
  }
}
