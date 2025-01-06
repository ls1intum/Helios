package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.branch.BranchService;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@Transactional
public class DeploymentService {

  private final DeploymentRepository deploymentRepository;
  private final GitHubService gitHubService;
  private final EnvironmentService environmentService;
  private final WorkflowService workflowService;
  private final AuthService authService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final PullRequestRepository pullRequestRepository;
  private final BranchService branchService;

  public DeploymentService(
      DeploymentRepository deploymentRepository,
      GitHubService gitHubService,
      EnvironmentService environmentService,
      WorkflowService workflowService, AuthService authService,
      HeliosDeploymentRepository heliosDeploymentRepository,
      PullRequestRepository pullRequestRepository, BranchService branchService) {
    this.deploymentRepository = deploymentRepository;
    this.gitHubService = gitHubService;
    this.environmentService = environmentService;
    this.workflowService = workflowService;
    this.authService = authService;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.pullRequestRepository = pullRequestRepository;
    this.branchService = branchService;
  }

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
    // TODO: Check DeployRequest validity before calling this method
    if (deployRequest.environmentId() == null || deployRequest.branchName() == null) {
      throw new DeploymentException("Environment ID and branch name must not be null");
    }

    // Get the user ID of the user who triggered the deployment
    final String user = this.authService.getUserId();

    // Get the deployment workflow set by the managers
    Workflow deploymentWorkflow = this.workflowService.getDeploymentWorkflow();
    if (deploymentWorkflow == null) {
      throw new DeploymentException("No deployment workflow found");
    }


    // Get the latest sha of the branch
    final String branchCommitSha = this.branchService.getBranchByName(deployRequest.branchName())
        .orElseThrow(() -> new DeploymentException("Branch not found"))
        .commitSha();

    // Lock the environment
    Environment environment =
        this.environmentService
            .lockEnvironment(deployRequest.environmentId())
            .orElseThrow(() -> new DeploymentException("Environment was already locked"));

    // 10 minutes timeout for redeployment
    if (!canRedeploy(environment, 10)) {
      throw new DeploymentException("Deployment is still in progress, please wait.");
    }


    // Create a new HeliosDeployment record
    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setEnvironment(environment);
    heliosDeployment.setUser(user);
    heliosDeployment.setStatus(HeliosDeployment.Status.WAITING);
    heliosDeployment.setBranchName(deployRequest.branchName());
    heliosDeployment = heliosDeploymentRepository.save(heliosDeployment);


    // Check if a PR exists for the branch
    final Optional<PullRequest> optionalPr = pullRequestRepository
        .findByRepositoryIdAndHeadRefName(
            environment.getRepository().getId(),
            deployRequest.branchName());


    // Build parameters for the workflow
    Map<String, Object> workflowParams = new HashMap<>();
    workflowParams.put("HELIOS_TRIGGERED_BY", user);
    workflowParams.put("HELIOS_BRANCH_NAME", deployRequest.branchName());
    workflowParams.put("HELIOS_BRANCH_HEAD_SHA", branchCommitSha);
    workflowParams.put("HELIOS_ENVIRONMENT_NAME", environment.getName());
    if (optionalPr.isPresent()) {
      final PullRequest pr = optionalPr.get();
      workflowParams.put("HELIOS_BUILD", "false");
      workflowParams.put("HELIOS_PR_NUMBER", pr.getNumber());
    } else {
      workflowParams.put("HELIOS_BUILD", "true");
      workflowParams.put("HELIOS_BUILD_TAG", "branch-" + heliosDeployment.getId().toString());
    }

    heliosDeployment.setWorkflowParams(workflowParams);
    heliosDeploymentRepository.save(heliosDeployment);

    try {
      this.gitHubService.dispatchWorkflow(
          environment.getRepository().getNameWithOwner(),
          deploymentWorkflow.getFileNameWithExtension(),
          deployRequest.branchName(),
          workflowParams);
    } catch (IOException e) {
      // Don't need to unlock the environment, since user might want to re-deploy
      heliosDeployment.setStatus(HeliosDeployment.Status.IO_ERROR);
      heliosDeploymentRepository.save(heliosDeployment);
      throw new DeploymentException("Failed to dispatch workflow due to IOException", e);
    }
  }

  public boolean canRedeploy(Environment environment, long timeoutMinutes) {
    // Fetch the most recent deployment for the environment
    Optional<HeliosDeployment> latestDeployment = heliosDeploymentRepository
        .findTopByEnvironmentOrderByCreatedAtDesc(environment);

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
        || deployment.getStatus() == HeliosDeployment.Status.WAITING) {
      OffsetDateTime now = OffsetDateTime.now();
      if (deployment.getStatusUpdatedAt().plusMinutes(timeoutMinutes).isAfter(now)) {
        throw new DeploymentException("Deployment is still in progress, please wait.");
      }

      deployment.setStatus(HeliosDeployment.Status.UNKNOWN);
      heliosDeploymentRepository.save(deployment);
    }
    return true;
  }

}
