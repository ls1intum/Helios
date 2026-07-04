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
import org.springframework.transaction.annotation.Transactional;
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

  @Transactional(readOnly = true)
  public List<WorkflowRunDto> getLatestWorkflowRunsByPullRequestIdAndHeadCommit(
      Long pullRequestId) {

    var pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
    if (pullRequest == null) {
      log.error("Pull request with id {} not found!", pullRequestId);
      return List.of();
    }

    return getLatestWorkflowRunsByBranchAndHeadCommitSha(pullRequest.getHeadRefName());
  }

  @Transactional(readOnly = true)
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

  @Transactional(readOnly = true)
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

  @Transactional(readOnly = true)
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
