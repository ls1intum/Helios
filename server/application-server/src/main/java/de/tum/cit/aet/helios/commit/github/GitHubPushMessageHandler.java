package de.tum.cit.aet.helios.commit.github;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubPushMessageHandler extends GitHubMessageHandler<GHEventPayload.Push> {
  private final GitHubCommitSyncService commitSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubBranchSyncService branchSyncService;
  private final GitHubService gitHubService;

  private GitHubPushMessageHandler(
      GitHubCommitSyncService commitSyncService,
      GitHubRepositorySyncService repositorySyncService,
      GitHubBranchSyncService branchSyncService,
      GitHubService gitHubService) {
    super(GHEventPayload.Push.class);
    this.commitSyncService = commitSyncService;
    this.repositorySyncService = repositorySyncService;
    this.branchSyncService = branchSyncService;
    this.gitHubService = gitHubService;
  }

  @Override
  protected void handleEvent(GHEventPayload.Push eventPayload) {
    String sha = eventPayload.getHeadCommit().getSha();
    String ref = eventPayload.getRef();

    log.info(
        "Received push event for repository: {}, commitSha: {}, ref: {}",
        eventPayload.getRepository().getFullName(),
        sha,
        ref);

    try {
      GHRepository repository = eventPayload.getRepository();
      repositorySyncService.processRepository(repository);

      var curRepo = gitHubService.getRepository(repository.getFullName());

      GHCommit commit = curRepo.getCommit(sha);
      commitSyncService.processCommit(commit, repository);

      GHBranch branch = curRepo.getBranch(ref.split("/")[2]);
      branchSyncService.processBranch(branch);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return;
  }

  @Override
  protected GHEvent getHandlerEvent() {
    return GHEvent.PUSH;
  }
}
