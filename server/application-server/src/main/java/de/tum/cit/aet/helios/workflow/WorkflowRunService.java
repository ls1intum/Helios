package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.workflow.pagination.PaginatedWorkflowRunsResponse;
import de.tum.cit.aet.helios.workflow.pagination.WorkflowRunPageRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

@Log4j2
@RequiredArgsConstructor
@Service
@Transactional
public class WorkflowRunService {

  private final WorkflowRunRepository workflowRunRepository;
  private final PullRequestRepository pullRequestRepository;
  private final BranchRepository branchRepository;

  public List<WorkflowRun> getAllWorkflowRuns() {
    return workflowRunRepository.findAll();
  }

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

  public void deleteWorkflowRun(Long workflowRunId) {
    workflowRunRepository.deleteById(workflowRunId);
  }
}
