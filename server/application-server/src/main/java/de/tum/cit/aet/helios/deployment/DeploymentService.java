package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistory;
import de.tum.cit.aet.helios.environment.EnvironmentLockHistoryRepository;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class DeploymentService {

  private final DeploymentRepository deploymentRepository;
  private final GitHubService gitHubService;
  private final EnvironmentService environmentService;
  private final AuthService authService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final EnvironmentLockHistoryRepository lockHistoryRepository;
  private final EnvironmentRepository environmentRepository;
  private final PullRequestRepository pullRequestRepository;
  private final GitRepoRepository gitRepoRepository;

  public Optional<DeploymentDto> getDeploymentById(Long id) {
    return deploymentRepository.findById(id).map(DeploymentDto::fromDeployment);
  }

  public List<DeploymentDto> getAllDeployments() {
    return deploymentRepository.findAll().stream()
        .map(DeploymentDto::fromDeployment)
        .collect(Collectors.toList());
  }

  public List<DeploymentDto> getDeploymentsByEnvironmentId(Long environmentId) {
    return deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId).stream()
        .map(DeploymentDto::fromDeployment)
        .collect(Collectors.toList());
  }

  public Optional<DeploymentDto> getLatestDeploymentByEnvironmentId(Long environmentId) {
    return deploymentRepository
        .findFirstByEnvironmentIdOrderByCreatedAtDesc(environmentId)
        .map(DeploymentDto::fromDeployment);
  }

  public void deployToEnvironment(DeployRequest deployRequest) {
    validateDeployRequest(deployRequest);
    validateEnvironmentAndPermissions(deployRequest.environmentId());

    Environment environment = lockEnvironment(deployRequest.environmentId());
    Workflow deploymentWorkflow = environment.getDeploymentWorkflow();

    if (deploymentWorkflow == null) {
      throw new NoSuchElementException(
          "No deployment workflow found for environment " + environment.getName());
    }
    // Set the PR associated with the deployment
    Optional<PullRequest> optionalPullRequest =
        pullRequestRepository.findOpenPrByBranchNameOrSha(
            RepositoryContext.getRepositoryId(),
            deployRequest.branchName(),
            deployRequest.commitSha());

    HeliosDeployment heliosDeployment =
        createHeliosDeployment(environment, deployRequest, optionalPullRequest);
    Map<String, Object> workflowParams = createWorkflowParams(deployRequest, environment);

    dispatchWorkflow(
        environment, deploymentWorkflow, deployRequest, workflowParams, heliosDeployment);
  }

  private void validateDeployRequest(DeployRequest deployRequest) {
    if (deployRequest.environmentId() == null || deployRequest.commitSha() == null) {
      throw new DeploymentException("Environment ID and commit sha must not be null");
    }
  }

  private void validateEnvironmentAndPermissions(Long environmentId) {
    Environment.Type environmentType =
        this.environmentService
            .getEnvironmentTypeById(environmentId)
            .orElseThrow(() -> new DeploymentException("Environment not found"));

    if (!canDeployToEnvironment(environmentType)) {
      throw new SecurityException("Insufficient permissions to deploy to this environment");
    }
  }

  private Environment lockEnvironment(Long environmentId) {
    // First, get the environment
    Environment environment =
        environmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new DeploymentException("Environment not found"));

    // Only attempt to lock if it's a test environment
    if (environment.getType() == Environment.Type.TEST) {
      environment =
          this.environmentService
              .lockEnvironment(environmentId)
              .orElseThrow(() -> new DeploymentException("Environment was already locked"));
    }

    if (!canRedeploy(environment, 20)) {
      throw new DeploymentException("Deployment is still in progress, please wait.");
    }

    return environment;
  }

  private HeliosDeployment createHeliosDeployment(
      Environment environment,
      DeployRequest deployRequest,
      Optional<PullRequest> optionalPullRequest) {
    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setEnvironment(environment);
    heliosDeployment.setUser(authService.getUserId());
    heliosDeployment.setStatus(HeliosDeployment.Status.WAITING);
    heliosDeployment.setBranchName(this.getDeploymentWorkflowBranch(environment, deployRequest));
    heliosDeployment.setSha(deployRequest.commitSha());
    heliosDeployment.setCreator(authService.getUserFromGithubId());
    heliosDeployment.setPullRequest(optionalPullRequest.orElse(null));
    return heliosDeploymentRepository.saveAndFlush(heliosDeployment);
  }

  private Map<String, Object> createWorkflowParams(
      DeployRequest deployRequest, Environment environment) {
    Map<String, Object> workflowParams = new HashMap<>();

    workflowParams.put("branch_name", deployRequest.branchName());
    workflowParams.put("environment_name", environment.getName());
    workflowParams.put("commit_sha", deployRequest.commitSha());
    workflowParams.put("triggered_by", authService.getPreferredUsername());

    return workflowParams;
  }

  public boolean canDeployToEnvironment(Environment.Type environmentType) {
    if (null != environmentType) {
      switch (environmentType) {
        case PRODUCTION -> {
          return authService.hasRole("ROLE_ADMIN");
        }
        case STAGING, TEST -> {
          return authService.hasRole("ROLE_WRITE")
              || authService.hasRole("ROLE_MAINTAINER")
              || authService.hasRole("ROLE_ADMIN");
        }
        default -> {}
      }
    }
    return false;
  }

  private String getDeploymentWorkflowBranch(Environment environment, DeployRequest deployRequest) {
    String workflowBranch = environment.getDeploymentWorkflowBranch();
    if (workflowBranch == null || workflowBranch.trim().isEmpty()) {
      workflowBranch = deployRequest.branchName();
    }
    return workflowBranch;
  }

  private void dispatchWorkflow(
      Environment environment,
      Workflow deploymentWorkflow,
      DeployRequest deployRequest,
      Map<String, Object> workflowParams,
      HeliosDeployment heliosDeployment) {
    heliosDeployment.setWorkflowParams(workflowParams);
    heliosDeploymentRepository.save(heliosDeployment);

    try {
      this.gitHubService.dispatchWorkflow(
          environment.getRepository().getNameWithOwner(),
          deploymentWorkflow.getFileNameWithExtension(),
          this.getDeploymentWorkflowBranch(environment, deployRequest),
          workflowParams);

      this.environmentService.markStatusAsChanged(environment);
    } catch (IOException e) {
      heliosDeployment.setStatus(HeliosDeployment.Status.IO_ERROR);
      heliosDeploymentRepository.save(heliosDeployment);

      // Pass through the detailed GitHub error message
      throw new DeploymentException(e.getMessage(), e);
    }
  }

  // TODO: Move this to a more appropriate location
  //  since we have the same code in two places
  //  below method (DeploymentService) & canUnlock method in EnvironmentService
  private boolean canRedeploy(Environment environment, long timeoutMinutes) {
    // Fetch the most recent deployment for the environment
    Optional<HeliosDeployment> latestDeployment =
        heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment);

    if (latestDeployment.isEmpty()) {
      // No prior deployments, safe to deploy
      return true;
    }

    HeliosDeployment deployment = latestDeployment.get();

    // Allow redeployment if the previous deployment failed
    if (deployment.getStatus() == HeliosDeployment.Status.FAILED
        || deployment.getStatus() == HeliosDeployment.Status.IO_ERROR
        || deployment.getStatus() == HeliosDeployment.Status.UNKNOWN) {
      return true;
    }

    // Check if timeout has elapsed
    if (deployment.getStatus() == HeliosDeployment.Status.IN_PROGRESS
        || deployment.getStatus() == HeliosDeployment.Status.WAITING
        || deployment.getStatus() == HeliosDeployment.Status.QUEUED) {
      OffsetDateTime now = OffsetDateTime.now();
      if (deployment.getStatusUpdatedAt().plusMinutes(timeoutMinutes).isAfter(now)) {
        return false;
      }

      deployment.setStatus(HeliosDeployment.Status.UNKNOWN);
      heliosDeploymentRepository.save(deployment);
    }
    return true;
  }

  /**
   * Retrieves and combines activity history for a specific environment, including deployments,
   * lock/unlock events, and potential Helios deployments, sorted chronologically descending.
   *
   * <p>The method aggregates data from three sources:
   *
   * <ol>
   *   <li><b>Real Deployments</b> - Fetches deployments from the deployment repository, converts
   *       them to DTOs, and sorts by creation time descending
   *   <li><b>Lock History</b> - Retrieves environment lock events and generates paired LOCK/UNLOCK
   *       events when applicable, converting them to DTOs
   *   <li><b>Helios Deployment</b> - Conditionally adds the Helios deployments if it's not matched
   *       with deployment records
   * </ol>
   *
   * <p>The final combined list is sorted by timestamp descending, with null timestamps placed last.
   *
   * @param environmentId The ID of the environment to fetch history for
   * @return Combined list of {@link ActivityHistoryDto} objects representing all activity, sorted
   *     by timestamp descending. Returns empty list if no activities found.
   * @implNote Special handling for Helios deployments:
   *     <ul>
   *       <li>Helios deployments are only added if they represent the latest deployment activity
   *       <li>Will not duplicate if already present in deployment records
   *       <li>Requires separate environment lookup to verify deployment recency
   *     </ul>
   */
  public List<ActivityHistoryDto> getActivityHistoryByEnvironmentId(Long environmentId) {
    // 1) Real deployments
    List<ActivityHistoryDto> deploymentDtos =
        deploymentRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId).stream()
            .map(ActivityHistoryDto::fromDeployment)
            .toList();

    // 3) Lock history (lock/unlock)
    List<EnvironmentLockHistory> lockHistories =
        lockHistoryRepository.findLockHistoriesByEnvironment(environmentId);
    List<ActivityHistoryDto> lockDtos =
        lockHistories.stream()
            .flatMap(
                lock -> {
                  ActivityHistoryDto lockEvent =
                      ActivityHistoryDto.fromEnvironmentLockHistory("LOCK_EVENT", lock);
                  if (lock.getUnlockedAt() != null) {
                    ActivityHistoryDto unlockEvent =
                        ActivityHistoryDto.fromEnvironmentLockHistory("UNLOCK_EVENT", lock);
                    return Stream.of(lockEvent, unlockEvent);
                  } else {
                    return Stream.of(lockEvent);
                  }
                })
            .toList();

    // 4) Combine the lists
    List<ActivityHistoryDto> combined = new ArrayList<>();
    combined.addAll(deploymentDtos);
    // combined.addAll(heliosDtos);
    combined.addAll(lockDtos);

    // 4a) Add heliosDeployment for latest deployment (deployment webhook not yet received) and all
    // the failed deployments
    var environment = environmentRepository.findById(environmentId).orElse(null);
    if (environment != null) {
      List<HeliosDeployment> heliosDeployments =
          heliosDeploymentRepository.findByEnvironmentAndDeploymentIdIsNull(environment);
      for (HeliosDeployment heliosDeployment : heliosDeployments) {
        ActivityHistoryDto heliosDto = ActivityHistoryDto.fromHeliosDeployment(heliosDeployment);
        combined.add(heliosDto);
      }
    }

    // 5) Sort by timestamp descending
    combined.sort(
        (a, b) -> {
          OffsetDateTime timeA = a.timestamp();
          OffsetDateTime timeB = b.timestamp();
          if (timeA == null && timeB == null) {
            return 0;
          }
          if (timeA == null) {
            return 1;
          }
          if (timeB == null) {
            return -1;
          }
          return timeB.compareTo(timeA);
        });

    return combined;
  }

  /**
   * Cancels an ongoing deployment by stopping its associated GitHub workflow run.
   *
   * @param cancelRequest The request containing the workflow run ID to cancel
   * @return Basic success message
   * @throws DeploymentException if the deployment cannot be canceled
   */
  @Transactional
  public String cancelDeployment(CancelDeploymentRequest cancelRequest) {
    Long workflowRunId = cancelRequest.workflowRunId();

    try {
      // We need to find a GitHub repository where we have permission to make API calls
      // Use the current repository context if available
      Long repositoryId = RepositoryContext.getRepositoryId();
      GitRepository repository =
          gitRepoRepository
              .findById(repositoryId)
              .orElseThrow(() -> new DeploymentException("Repository not found in context"));

      String repoNameWithOwner = repository.getNameWithOwner();

      // Call GitHub to cancel the workflow
      gitHubService.cancelWorkflowRun(repoNameWithOwner, workflowRunId);

      return "Workflow cancellation request sent successfully";
    } catch (IOException e) {
      throw new DeploymentException("Failed to cancel workflow run: " + e.getMessage(), e);
    }
  }

  /**
   * Retrieves detailed job status information for a GitHub workflow run.
   *
   * @param workflowRunId Workflow run ID to get job status for
   * @return List of workflow jobs with their steps
   * @throws DeploymentException if there's an error fetching the workflow job status
   */
  public WorkflowJobsResponse getWorkflowJobStatus(Long workflowRunId) {
    try {
      // Get repository ID from context
      Long repositoryId = RepositoryContext.getRepositoryId();

      // Get repository details
      GitRepository repository =
          gitRepoRepository
              .findById(repositoryId)
              .orElseThrow(() -> new NoSuchElementException("Repository not found"));

      String repoNameWithOwner = repository.getNameWithOwner();

      // Call GitHub service to get raw JSON response
      String rawJsonResponse = gitHubService.getWorkflowJobStatus(repoNameWithOwner, workflowRunId);

      // Parse JSON response to GitHub API structure
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
      objectMapper.registerModule(new JavaTimeModule());
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

      try {
        return objectMapper.readValue(rawJsonResponse, WorkflowJobsResponse.class);
      } catch (Exception e) {
        log.error("Failed to parse GitHub API response: {}", e.getMessage());
        throw new DeploymentException("Failed to parse GitHub API response: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      log.error("Failed to fetch workflow job status: {}", e.getMessage());
      throw new DeploymentException("Failed to fetch workflow job status: " + e.getMessage(), e);
    } catch (NoSuchElementException e) {
      log.error("Repository not found: {}", e.getMessage());
      throw new DeploymentException("Repository not found: " + e.getMessage(), e);
    }
  }
}
