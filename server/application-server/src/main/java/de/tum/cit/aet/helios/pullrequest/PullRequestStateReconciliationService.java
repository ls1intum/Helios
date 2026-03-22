package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.pullrequest.github.GitHubPullRequestSyncService;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class PullRequestStateReconciliationService {

  private static final Pattern NOT_FOUND_PATTERN =
      Pattern.compile("not found|\"status\"\\s*:\\s*\"404\"", Pattern.CASE_INSENSITIVE);

  private final PullRequestRepository pullRequestRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubPullRequestSyncService pullRequestSyncService;
  private final GitHubFacade github;

  public PullRequestStateReconciliationResultDto reconcilePullRequestState(
      Long repositoryId, boolean dryRun)
      throws IOException {
    GitRepository repository =
        gitRepoRepository
            .findByRepositoryId(repositoryId)
            .orElseThrow(
                () -> new EntityNotFoundException("Repository not found with ID: " + repositoryId));

    org.kohsuke.github.GHRepository ghRepository;
    try {
      ghRepository = github.getRepository(repository.getNameWithOwner());
    } catch (IOException e) {
      if (isNotFound(e)) {
        throw new EntityNotFoundException(
            "GitHub repository not found for repository ID: " + repositoryId);
      }
      throw e;
    }

    List<PullRequest> pullRequests =
        pullRequestRepository.findByRepositoryRepositoryIdAndStateOrderByUpdatedAtDesc(
            repositoryId, Issue.State.OPEN);
    List<Long> updatedPullRequestIds = new ArrayList<>();
    List<Integer> updatedPullRequestNumbers = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    int unchangedCount = 0;

    for (PullRequest pullRequest : pullRequests) {
      ReconciliationOutcome outcome =
          reconcilePullRequest(pullRequest, ghRepository, repository.getNameWithOwner(), dryRun);
      switch (outcome.status()) {
        case UPDATED -> {
          updatedPullRequestIds.add(pullRequest.getId());
          updatedPullRequestNumbers.add(pullRequest.getNumber());
        }
        case UNCHANGED -> {
          unchangedCount++;
        }
        case ERROR -> errors.add(outcome.errorMessage());
        default ->
            throw new IllegalStateException(
                "Unexpected reconciliation status: " + outcome.status());
      }
    }

    return new PullRequestStateReconciliationResultDto(
        dryRun,
        repository.getRepositoryId(),
        repository.getNameWithOwner(),
        pullRequests.size(),
        updatedPullRequestIds.size(),
        List.copyOf(updatedPullRequestIds),
        List.copyOf(updatedPullRequestNumbers),
        unchangedCount,
        errors.size(),
        List.copyOf(errors));
  }

  static boolean isNotFound(IOException exception) {
    return exception.getMessage() != null
        && NOT_FOUND_PATTERN.matcher(exception.getMessage()).find();
  }

  private ReconciliationOutcome reconcilePullRequest(
      PullRequest pullRequest,
      org.kohsuke.github.GHRepository ghRepository,
      String repositoryNameWithOwner,
      boolean dryRun) {
    try {
      GHPullRequest gitHubPullRequest = ghRepository.getPullRequest(pullRequest.getNumber());
      if (gitHubPullRequest.getState() != GHIssueState.CLOSED) {
        return ReconciliationOutcome.unchanged();
      }

      if (dryRun) {
        log.info(
            "DRY-RUN: Would reconcile local pull request {} (#{}) "
                + "to closed state for repository {}.",
            pullRequest.getId(),
            pullRequest.getNumber(),
            repositoryNameWithOwner);
      } else {
        pullRequestSyncService.processPullRequest(gitHubPullRequest);
        log.info(
            "Reconciled local pull request {} (#{}) to closed state for repository {}.",
            pullRequest.getId(),
            pullRequest.getNumber(),
            repositoryNameWithOwner);
      }
      return ReconciliationOutcome.updated();
    } catch (Exception e) {
      log.warn(
          "Failed to reconcile pull request {} (#{}) in repository {}: {}",
          pullRequest.getId(),
          pullRequest.getNumber(),
          repositoryNameWithOwner,
          e.getMessage(),
          e);
      return ReconciliationOutcome.error(
          "Failed to reconcile PR #%d (id=%d): %s"
              .formatted(pullRequest.getNumber(), pullRequest.getId(), e.getMessage()));
    }
  }

  private record ReconciliationOutcome(ReconciliationStatus status, String errorMessage) {

    private static ReconciliationOutcome updated() {
      return new ReconciliationOutcome(ReconciliationStatus.UPDATED, null);
    }

    private static ReconciliationOutcome unchanged() {
      return new ReconciliationOutcome(ReconciliationStatus.UNCHANGED, null);
    }

    private static ReconciliationOutcome error(String errorMessage) {
      return new ReconciliationOutcome(ReconciliationStatus.ERROR, errorMessage);
    }
  }

  private enum ReconciliationStatus {
    UPDATED,
    UNCHANGED,
    ERROR
  }
}
