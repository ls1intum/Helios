package de.tum.cit.aet.helios.pullrequest.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class GitHubPullRequestMessageHandler
    extends GitHubMessageHandler<GHEventPayload.PullRequest> {

  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubService gitHubService;
  private final Environment environment;

  @Override
  protected Class<GHEventPayload.PullRequest> getPayloadClass() {
    return GHEventPayload.PullRequest.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.PULL_REQUEST;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.PullRequest eventPayload) {
    log.info(
        "Received pull request event for repository: {}, pull request: {}, action: {}",
        eventPayload.getRepository().getFullName(),
        eventPayload.getPullRequest().getNumber(),
        eventPayload.getAction());
    repositorySyncService.processRepository(eventPayload.getRepository());
    // We don't need to handle the deleted action here, as pull requests are not deleted
    pullRequestSyncService.processPullRequest(eventPayload.getPullRequest());
    // Create a commit status for the pull request
    createCommitStatus(eventPayload);
  }

  /**
   * Creates a commit status for a pull request based on specific triggering actions.
   *
   * <p>The following actions will trigger a commit status creation:
   * <ul>
   *   <li>synchronize - When a pull request's head branch is updated</li>
   *   <li>opened - When a pull request is first created</li>
   *   <li>reopened - When a previously closed pull request is reopened</li>
   *   <li>ready_for_review - When a draft pull request is marked as ready for review</li>
   * </ul>
   *
   * <p>In the staging environment, commit statuses are only created for the
   * ls1intum/Helios repository.
   * In production and development environments, commit statuses are created for all repositories.
   *
   * @param eventPayload The GitHub pull request event payload
   */
  private void createCommitStatus(GHEventPayload.PullRequest eventPayload) {
    Set<String> actionsTriggeringCommitStatus = Set.of(
        "synchronize",
        "opened",
        "reopened",
        "ready_for_review"
    );

    // Check if we should skip based on repository and environment
    String repoFullName = eventPayload.getRepository().getFullName();
    boolean isStagingEnvironment = environment.acceptsProfiles(Profiles.of("staging"));
    boolean isHeliosRepo = "ls1intum/Helios".equals(repoFullName);

    if (isStagingEnvironment && !isHeliosRepo) {
      log.info("Skipping commit status creation for repository {} in staging environment.",
          repoFullName);
      return;
    }

    if (actionsTriggeringCommitStatus.contains(eventPayload.getAction().toLowerCase())) {
      gitHubService.createCommitStatusForPullRequest(eventPayload.getPullRequest());
    }
  }
}
