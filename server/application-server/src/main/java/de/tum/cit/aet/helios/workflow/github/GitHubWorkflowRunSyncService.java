package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.util.DateUtil;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class GitHubWorkflowRunSyncService {
  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubWorkflowRunConverter workflowRunConverter;
  private final GitRepoRepository gitRepoRepository;
  private final PullRequestRepository pullRequestRepository;

  public GitHubWorkflowRunSyncService(
      WorkflowRunRepository workflowRunRepository,
      GitHubWorkflowRunConverter workflowRunConverter,
      GitRepoRepository gitRepoRepository,
      PullRequestRepository pullRequestRepository) {
    this.workflowRunRepository = workflowRunRepository;
    this.workflowRunConverter = workflowRunConverter;
    this.gitRepoRepository = gitRepoRepository;
    this.pullRequestRepository = pullRequestRepository;
  }

  /**
   * Synchronizes all worfklwo runs from the specified GitHub repositories.
   *
   * @param repositories the list of GitHub repositories to sync workflow runs from
   * @param since an optional date to filter pull requests by their last update
   * @return a list of GitHub workflow runs that were successfully fetched and processed
   */
  public List<GHWorkflowRun> syncRunsOfAllRepositories(
      List<GHRepository> repositories, Optional<OffsetDateTime> since) {
    return repositories.stream()
        .map(repository -> syncRunsOfRepository(repository, since))
        .flatMap(List::stream)
        .toList();
  }

  /**
   * Synchronizes all workflow runs from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync workflow runs from
   * @param since an optional date to filter workflow runs by their last update
   * @return a list of GitHub workflow runs requests that were successfully fetched and processed
   */
  public List<GHWorkflowRun> syncRunsOfRepository(
      GHRepository repository, Optional<OffsetDateTime> since) {
    var iterator = repository.queryWorkflowRuns().list().withPageSize(100).iterator();

    var sinceDate = since.map(date -> Date.from(date.toInstant()));

    var workflowRuns = new ArrayList<GHWorkflowRun>();

    while (iterator.hasNext()) {
      var ghPullRequests = iterator.nextPage();
      var keepPullRequests =
          ghPullRequests.stream()
              .filter(
                  pullRequest -> {
                    try {
                      return sinceDate.isEmpty()
                          || pullRequest.getUpdatedAt().after(sinceDate.get());
                    } catch (IOException e) {
                      log.error(
                          "Failed to filter workflow run {}: {}",
                          pullRequest.getId(),
                          e.getMessage());
                      return false;
                    }
                  })
              .toList();

      workflowRuns.addAll(keepPullRequests);
      if (keepPullRequests.size() != ghPullRequests.size()) {
        break;
      }
    }

    workflowRuns.forEach(this::processRun);

    return workflowRuns;
  }

  @Transactional
  public WorkflowRun processRun(GHWorkflowRun ghWorkflowRun) {
    var result =
        workflowRunRepository
            .findById(ghWorkflowRun.getId())
            .map(
                workflowRun -> {
                  try {
                    if (workflowRun.getUpdatedAt() == null
                        || workflowRun
                            .getUpdatedAt()
                            .isBefore(
                                DateUtil.convertToOffsetDateTime(ghWorkflowRun.getUpdatedAt()))) {
                      return workflowRunConverter.update(ghWorkflowRun, workflowRun);
                    }
                    return workflowRun;
                  } catch (IOException e) {
                    log.error(
                        "Failed to update worfklow run {}: {}",
                        ghWorkflowRun.getId(),
                        e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> workflowRunConverter.convert(ghWorkflowRun));

    if (result == null) {
      return null;
    }

    // Link with existing repository if not already linked
    if (result.getRepository() == null) {
      var nameWithOwner = ghWorkflowRun.getRepository().getFullName();
      var repository = gitRepoRepository.findByNameWithOwner(nameWithOwner);

      if (repository != null) {
        result.setRepository(repository);
      }
    }

    try {
      Set<PullRequest> pullRequests = new HashSet<>();

      ghWorkflowRun
          .getPullRequests()
          .forEach(
              pullRequest -> {
                var pr = pullRequestRepository.findById(pullRequest.getId());

                if (!pr.isEmpty()) {
                  pullRequests.add(pr.get());
                }
              });

      result.setPullRequests(pullRequests);
    } catch (IOException e) {
      log.error(
          "Failed to process pull requests for workflow run {}: {}",
          ghWorkflowRun.getId(),
          e.getMessage());
    }

    return workflowRunRepository.save(result);
  }
}
