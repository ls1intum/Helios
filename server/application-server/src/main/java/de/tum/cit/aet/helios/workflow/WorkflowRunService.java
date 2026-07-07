package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.tests.TestSuiteRepository;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Log4j2
@RequiredArgsConstructor
@Service
public class WorkflowRunService {

  private final WorkflowRunRepository workflowRunRepository;
  private final PullRequestRepository pullRequestRepository;
  private final BranchRepository branchRepository;
  private final GitHubService gitHubService;
  private final GitRepoRepository gitRepoRepository;
  private final TestSuiteRepository testSuiteRepository;

  private Stream<WorkflowRun> getLatestWorkflowRuns(List<WorkflowRun> runs) {
    return runs.stream()
        .collect(Collectors.groupingBy(WorkflowRun::getWorkflowId))
        .values()
        .stream()
        .map(
            workflowRuns ->
                workflowRuns.stream().max(Comparator.comparing(WorkflowRun::getRunNumber)).get());
  }

  public List<WorkflowRunDto> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      Long pullRequestId) {

    var pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
    if (pullRequest == null) {
      log.error("Pull request with id {} not found!", pullRequestId);
      return List.of();
    }

    return getLatestWorkflowRunsByBranchAndHeadCommitSha(pullRequest.getHeadRefName());
  }

  public List<WorkflowRunDto> getLatestWorkflowRunsByBranchAndHeadCommitSha(String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    var branch =
        branchRepository.findByNameAndRepositoryRepositoryId(branchName, repositoryId).orElse(null);
    if (branch == null) {
      log.error("Branch with name {} not found!", branchName);
      return List.of();
    }
    var runs =
        workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
            branchName, branch.getCommitSha(), repositoryId);
    var latestRuns = getLatestWorkflowRuns(runs);

    return latestRuns.map(WorkflowRunDto::fromWorkflowRun).toList();
  }

  /** Resolves the {@link PipelineRunContext} for a branch's head commit (with previous-commit). */
  public PipelineRunContext getPipelineRunContextForBranch(String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    var branch =
        branchRepository.findByNameAndRepositoryRepositoryId(branchName, repositoryId).orElse(null);
    if (branch == null) {
      log.error("Branch with name {} not found!", branchName);
      return PipelineRunContext.empty();
    }
    return buildPipelineRunContext(
        branch.getCommitSha(),
        sha ->
            workflowRunRepository.findByHeadBranchAndHeadShaAndRepositoryRepositoryId(
                branchName, sha, repositoryId),
        exclude ->
            workflowRunRepository.findNthLatestCommitShaBehindHeadByBranchAndRepoId(
                branchName, repositoryId, 0, exclude));
  }

  /** Resolves the {@link PipelineRunContext} for a pull request's head commit. */
  public PipelineRunContext getPipelineRunContextForPullRequest(Long pullRequestId) {
    // No repository scoping needed here: the PR id is globally unique and every run lookup below is
    // scoped to runs associated with this PR, so there is no cross-tenant leak.
    var pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
    if (pullRequest == null) {
      log.error("Pull request with id {} not found!", pullRequestId);
      return PipelineRunContext.empty();
    }
    // Scope runs and commit history to the same PR-associated universe: the previous/fallback SHAs
    // are derived from runs joined to this PR, so the runs we then fetch for a SHA must be too — a
    // branch+sha lookup could resolve to a merge-queue commit whose runs live under another branch.
    return buildPipelineRunContext(
        pullRequest.getHeadSha(),
        sha -> workflowRunRepository.findByPullRequestsIdAndHeadSha(pullRequestId, sha),
        exclude ->
            workflowRunRepository.findNthLatestCommitShaBehindHeadByPullRequestId(
                pullRequestId, 0, exclude));
  }

  /**
   * Resolves which commit a pipeline should display and its neighbours, using only ingested runs.
   *
   * <p>Prefers the head commit; when it has no runs yet — just pushed, gated at the run level, or a
   * missed push webhook — falls back to the most recent commit that did run CI (flagged {@code
   * upToDate=false}) so the developer still sees real progress instead of a blank skeleton. Also
   * resolves the immediately-preceding commit, but only while the displayed commit is still running
   * (once it is terminal its own node row tells the whole story).
   *
   * @param runsForSha returns the ingested runs for a commit SHA; must draw from the same universe
   *     as {@code previousShaFinder} (both branch-scoped, or both PR-association-scoped) so every
   *     resolved commit is guaranteed to have runs to render
   * @param previousShaFinder returns the newest commit-with-runs that differs from the given commit
   *     (offset 0 of the run-derived commit history)
   */
  private PipelineRunContext buildPipelineRunContext(
      String headSha,
      java.util.function.Function<String, List<WorkflowRun>> runsForSha,
      java.util.function.Function<String, Optional<String>> previousShaFinder) {
    if (headSha == null) {
      return PipelineRunContext.empty();
    }
    List<WorkflowRun> headRuns = runsForSha.apply(headSha);

    final String displayedSha;
    final boolean upToDate;
    if (!headRuns.isEmpty()) {
      displayedSha = headSha;
      upToDate = true;
    } else {
      final String fallback = previousShaFinder.apply(headSha).orElse(null);
      final List<WorkflowRun> fallbackRuns =
          fallback == null ? List.of() : runsForSha.apply(fallback);
      if (fallbackRuns.isEmpty()) {
        // No runs we can show: either CI has never run for this branch/PR, or the newest commit in
        // the run history lives in a different scope (e.g. a merge-queue branch). Either way show
        // the head as a legitimate "not running yet" rather than an empty grid under a stale SHA.
        return new PipelineRunContext(List.of(), headSha, true, null, List.of());
      }
      displayedSha = fallback;
      upToDate = false;
      headRuns = fallbackRuns;
    }

    final List<WorkflowRunDto> currentRuns =
        getLatestWorkflowRuns(headRuns).map(WorkflowRunDto::fromWorkflowRun).toList();

    final boolean currentComplete =
        currentRuns.stream().allMatch(r -> r.status() == WorkflowRun.Status.COMPLETED);
    if (currentComplete) {
      return new PipelineRunContext(currentRuns, displayedSha, upToDate, null, List.of());
    }
    final String previousSha = previousShaFinder.apply(displayedSha).orElse(null);
    final List<WorkflowRunDto> previousRuns =
        previousSha == null
            ? List.of()
            : getLatestWorkflowRuns(runsForSha.apply(previousSha))
                .map(WorkflowRunDto::fromWorkflowRun)
                .toList();
    return new PipelineRunContext(currentRuns, displayedSha, upToDate, previousSha, previousRuns);
  }

  public PaginatedWorkflowRunsResponse getPaginatedWorkflowRuns(WorkflowRunPageRequest request) {
    Long repositoryId = RepositoryContext.getRepositoryId();

    Sort sort = resolveSort(request);
    Pageable pageable = PageRequest.of(Math.max(request.getPage() - 1, 0), request.getSize(), sort);

    Specification<WorkflowRun> spec = buildWorkflowRunSpecification(request, repositoryId);

    Page<WorkflowRun> resultPage = workflowRunRepository.findAll(spec, pageable);

    List<WorkflowRunDto> runs =
        resultPage.getContent().stream().map(WorkflowRunDto::fromWorkflowRun).toList();

    return new PaginatedWorkflowRunsResponse(
        runs,
        request.getPage(),
        request.getSize(),
        resultPage.getTotalElements(),
        resultPage.getTotalPages());
  }

  private Specification<WorkflowRun> buildWorkflowRunSpecification(
      WorkflowRunPageRequest request, Long repositoryId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always scope to current repository
      predicates.add(cb.equal(root.get("repository").get("repositoryId"), repositoryId));

      // Search term across a few meaningful fields
      if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
        String term = "%" + request.getSearchTerm().trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), term),
                cb.like(cb.lower(root.get("displayTitle")), term),
                cb.like(cb.lower(root.get("headBranch")), term),
                cb.like(cb.lower(root.get("headSha")), term)));
      }

      // Filter by status/conclusion based on filter type
      switch (request.getFilterType()) {
        case NOT_STARTED:
          predicates.add(
              root.get("status")
                  .in(
                      WorkflowRun.Status.QUEUED,
                      WorkflowRun.Status.WAITING,
                      WorkflowRun.Status.REQUESTED,
                      WorkflowRun.Status.PENDING));
          break;
        case IN_PROGRESS:
          predicates.add(cb.equal(root.get("status"), WorkflowRun.Status.IN_PROGRESS));
          break;
        case CANCELLED:
          predicates.add(cb.equal(root.get("conclusion"), WorkflowRun.Conclusion.CANCELLED));
          break;
        case SUCCESS:
          predicates.add(cb.equal(root.get("conclusion"), WorkflowRun.Conclusion.SUCCESS));
          break;
        case FAILURE:
          predicates.add(
              root.get("conclusion")
                  .in(
                      WorkflowRun.Conclusion.FAILURE,
                      WorkflowRun.Conclusion.STARTUP_FAILURE,
                      WorkflowRun.Conclusion.TIMED_OUT));
          break;
        case ACTION_REQUIRED:
          predicates.add(
              cb.or(
                  cb.equal(root.get("status"), WorkflowRun.Status.ACTION_REQUIRED),
                  cb.equal(root.get("conclusion"), WorkflowRun.Conclusion.ACTION_REQUIRED)));
          break;
        case ALL:
        default:
          break;
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Sort resolveSort(WorkflowRunPageRequest request) {
    String sortField = request.getSortField();
    String sortDirection = request.getSortDirection();

    // Default: newest first by start time, then creation time
    Sort defaultSort =
        Sort.by(Sort.Direction.DESC, "runStartedAt").and(Sort.by(Sort.Direction.DESC, "createdAt"));

    if (sortField == null || sortField.isBlank()) {
      return defaultSort;
    }

    String property;
    // Map API sort fields to entity properties; fall back to default
    switch (sortField) {
      case "name":
        property = "name";
        break;
      case "status":
        property = "status";
        break;
      case "branch":
        property = "headBranch";
        break;
      case "runStartedAt":
        property = "runStartedAt";
        break;
      case "updatedAt":
        property = "updatedAt";
        break;
      default:
        return defaultSort;
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, property).and(defaultSort);
  }

  public WorkflowRunDto getWorkflowRunById(Long runId) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return getWorkflowRunForCurrentRepository(runId, repositoryId, false)
        .map(WorkflowRunDto::fromWorkflowRun)
        .orElseThrow(() -> new EntityNotFoundException(
            "Workflow run with id %d not found".formatted(runId)));
  }

  public void cancelWorkflowRun(Long runId) {
    executeWorkflowRunAction(runId, gitHubService::cancelWorkflowRun, "cancel", false);
  }

  public void reRunWorkflow(Long runId) {
    executeWorkflowRunAction(runId, gitHubService::reRunWorkflow, "re-run", true);
  }

  public void reRunFailedJobs(Long runId) {
    executeWorkflowRunAction(runId, gitHubService::reRunFailedJobs, "re-run failed jobs for", true);
  }

  @FunctionalInterface
  private interface WorkflowRunAction {
    void execute(String repoNameWithOwner, long runId) throws IOException;
  }

  private void executeWorkflowRunAction(
      Long runId, WorkflowRunAction action, String actionName, boolean resetTestState) {
    try {
      Long repositoryId = RepositoryContext.getRepositoryId();
      var repository =
          gitRepoRepository
              .findById(repositoryId)
              .orElseThrow(() -> new EntityNotFoundException(
                  "Repository with id %d not found".formatted(repositoryId)));

      var workflowRun = getWorkflowRunForCurrentRepository(runId, repositoryId, true)
          .orElseThrow(() -> new EntityNotFoundException(
              "Workflow run with id %d not found".formatted(runId)));

      action.execute(repository.getNameWithOwner(), runId);

      if (resetTestState) {
        resetTestStateForRerun(workflowRun);
      }
    } catch (IOException e) {
      log.error("Failed to {} workflow run {}: {}", actionName, runId, e.getMessage());
      throw new RuntimeException(
          "Failed to %s workflow run with id %d".formatted(actionName, runId), e);
    }
  }

  private Optional<WorkflowRun> getWorkflowRunForCurrentRepository(
      Long runId, Long repositoryId, boolean actionRequest) {
    return workflowRunRepository.findByIdAndRepositoryRepositoryId(runId, repositoryId).or(() -> {
      if (actionRequest && workflowRunRepository.findById(runId).isPresent()) {
        log.warn(
            "Blocked workflow run action for run {} in repository {} due to repository mismatch",
            runId,
            repositoryId);
      }
      return Optional.empty();
    });
  }

  private void resetTestStateForRerun(WorkflowRun workflowRun) {
    var workflow = workflowRun.getWorkflow();
    if (workflow == null || CollectionUtils.isEmpty(workflow.getTestTypes())) {
      return;
    }

    var existingTestSuites = testSuiteRepository.findByWorkflowRunId(workflowRun.getId());
    if (!existingTestSuites.isEmpty()) {
      testSuiteRepository.deleteAll(existingTestSuites);
    }

    workflowRun.setTestSuites(null);
    workflowRun.setTestProcessingStatus(null);
    workflowRunRepository.save(workflowRun);
  }
}
