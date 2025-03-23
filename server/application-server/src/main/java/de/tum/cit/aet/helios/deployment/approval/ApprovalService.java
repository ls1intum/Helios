package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
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

    log.info(
        "Approving deployment: {} {} {} {}", deploymentSource, gitRepository, environment, user);

    Boolean shouldApprove = shouldApprove();

    if (!shouldApprove) {
      log.info("Ignoring non-Helios deployment approval");
      return;
    }
    Boolean isHeliosBot = user.getLogin().equals("helios-bot");
    log.info("Is Helios Bot: {}", isHeliosBot);

    HeliosDeployment heliosDeployment =
        heliosDeploymentRepository.findByDeploymentId(deploymentSource.getId()).orElse(null);

    log.info("Helios Deployment: {}", heliosDeployment);
    if (heliosDeployment == null) {
      log.error("Deployment with ID {} not found", deploymentSource.getId());
      return;
    }

    Long workflowRunId =
        Long.valueOf(
            heliosDeployment
                .getWorkflowRunHtmlUrl()
                .substring(heliosDeployment.getWorkflowRunHtmlUrl().lastIndexOf("/") + 1));

    String repoNameWithOwner = gitRepository.getNameWithOwner();
    String githubUserLogin = heliosDeployment.getCreator().getLogin();

    log.info("Workflow Run ID: {}", workflowRunId);
    log.info("Repo Name with Owner: {}", repoNameWithOwner);
    log.info("GitHub User Login: {}", githubUserLogin);
    try {
      this.gitHubService.approveDeploymentOnBehalfOfUser(
          repoNameWithOwner, workflowRunId, environment.getId(), githubUserLogin);
    } catch (IOException e) {
      log.error("Error approving deployment: {}", e.getMessage());
    }

    // cancel workflow if not possible to approve
    // maybe add a new status to deployment "NOT_ENOUGH_PERMISSIONS"
  }

  private Boolean shouldApprove() {
    // check if triggered via helios
    return true;
  }
}
