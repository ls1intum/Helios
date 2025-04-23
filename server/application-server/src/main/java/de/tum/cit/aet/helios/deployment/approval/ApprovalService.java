package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApprovalService {
  private final GitHubService gitHubService;
  private final HeliosDeploymentRepository heliosDeploymentRepository;

  public void reviewDeployment(
      DeploymentSource deploymentSource,
      GitRepository gitRepository,
      Environment environment,
      User user) {

    Optional<HeliosDeployment> heliosDeploymentOpt =
        heliosDeploymentRepository.findByDeploymentId(deploymentSource.getId());

    // If deployment not found in Helios, it was triggered directly from GitHub
    if (heliosDeploymentOpt.isEmpty()) {
      log.info("Skipping approval for non-Helios deployment with ID {}", deploymentSource.getId());
      return;
    }

    HeliosDeployment heliosDeployment = heliosDeploymentOpt.get();

    // Check if this is a Helios deployment with workflow run ID
    if (heliosDeployment.getWorkflowRunId() == null) {
      // Workflow run ID missing - this is unusual for a Helios deployment
      log.warn(
          "Missing workflow run ID for Helios deployment with ID {}. Possible sync issue.",
          deploymentSource.getId());
      return;
    }

    log.info("Approving Helios Deployment: {}", heliosDeployment);

    Long workflowRunId = heliosDeployment.getWorkflowRunId();
    String repoNameWithOwner = gitRepository.getNameWithOwner();
    String githubUserLogin = heliosDeployment.getCreator().getLogin();

    try {
      this.gitHubService.approveDeploymentOnBehalfOfUser(
          repoNameWithOwner, workflowRunId, environment.getId(), githubUserLogin);
    } catch (IOException e) {
      log.error("Error approving deployment: {}", e.getMessage());
    }
  }
}
