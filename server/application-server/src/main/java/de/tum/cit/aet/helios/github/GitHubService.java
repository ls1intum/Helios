package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.auth.AuthService;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@Transactional
public class GitHubService {
  private final GitHubFacade github;

  private final GitHubConfig gitHubConfig;

  private final ObjectMapper objectMapper;

  private final OkHttpClient okHttpClient;

  private final AuthService authService;

  private final GitHubClientManager clientManager;

  private GHOrganization gitHubOrganization;

  public GitHubService(
      GitHubFacade github,
      GitHubConfig gitHubConfig,
      ObjectMapper objectMapper,
      OkHttpClient okHttpClient,
      AuthService authService,
      GitHubClientManager clientManager) {
    this.github = github;
    this.gitHubConfig = gitHubConfig;
    this.objectMapper = objectMapper;
    this.okHttpClient = okHttpClient;
    this.authService = authService;
    this.clientManager = clientManager;
  }

  public Builder getRequestBuilder() {
    return new Request.Builder()
        .header("Authorization", "token " + clientManager.getCurrentToken())
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
   * @param repoNameWithOwners   the repository name with owners
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
   * @param repoNameWithOwners   the repository name with owners
   * @param workflowFileNameOrId the workflow file name or ID
   * @param ref                  the reference (branch or tag) to run the workflow on
   * @param inputs               the inputs for the workflow
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
   * @param repository      the GitHub repository as a GHRepository object
   * @param environmentName the environment name
   * @param since           an optional timestamp to fetch deployments since
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
   * @param username     the GitHub username.
   * @return GitHubRepositoryRoleDto containing the role information.
   * @throws IOException if there is an error making the GitHub API call or
   *                     processing the response.
   * @throws IllegalArgumentException if the repository ID or username is null
   *                                  or empty.
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
}
