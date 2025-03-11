package de.tum.cit.aet.helios.commit.github;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubPushMessageHandler extends GitHubMessageHandler<GHEventPayload.Push> {
  private final GitHubCommitSyncService commitSyncService;
  private final GitHubRepositorySyncService repositorySyncService;
  private final GitHubBranchSyncService branchSyncService;
  private final GitHubService gitHubService;

  @Override
  protected Class<GHEventPayload.Push> getPayloadClass() {
    return GHEventPayload.Push.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.PUSH;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Push eventPayload) {
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
}
