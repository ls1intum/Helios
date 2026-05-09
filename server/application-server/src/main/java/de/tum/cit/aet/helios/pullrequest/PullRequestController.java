package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.permissions.RepositoryAuthorizationService;
import de.tum.cit.aet.helios.pullrequest.pagination.PaginatedPullRequestsResponse;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/pullrequests")
public class PullRequestController {

  private final PullRequestService pullRequestService;
  private final PullRequestStateReconciliationService pullRequestStateReconciliationService;
  private final RepositoryAuthorizationService repositoryAuthorizationService;

  @GetMapping
  public ResponseEntity<PaginatedPullRequestsResponse> getPullRequests(
      @ParameterObject PullRequestPageRequest pageRequest) {
    PaginatedPullRequestsResponse response =
        pullRequestService.getPaginatedPullRequests(pageRequest);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PullRequestInfoDto> getPullRequestById(@PathVariable Long id) {
    return pullRequestService
        .getPullRequestById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // TODO: Generalize filtering with repositoryId
  @GetMapping("/repository/{id}")
  public ResponseEntity<List<PullRequestInfoDto>> getPullRequestByRepositoryId(
      @PathVariable Long id) {
    List<PullRequestInfoDto> pullRequests = pullRequestService.getPullRequestByRepositoryId(id);
    return ResponseEntity.ok(pullRequests);
  }

  @GetMapping("/repository/{repoId}/pr/{number}")
  public ResponseEntity<PullRequestInfoDto> getPullRequestByRepositoryIdAndNumber(
      @PathVariable Long repoId, @PathVariable Integer number) {
    return pullRequestService
        .getPullRequestByRepositoryIdAndNumber(repoId, number)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/repository/{repoId}/filter-options")
  public ResponseEntity<PullRequestFilterOptionsDto> getPullRequestFilterOptionsByRepositoryId(
      @PathVariable Long repoId) {
    return ResponseEntity.ok(pullRequestService.getPullRequestFilterOptionsByRepositoryId(repoId));
  }

  @PostMapping("/{pr}/pin")
  public ResponseEntity<Void> setPrPinnedByNumber(
      @PathVariable Long pr, @RequestParam(name = "isPinned") Boolean isPinned) {
    pullRequestService.setPrPinnedByNumberAndUserId(pr, isPinned);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/repository/{repositoryId}/reconcile-state")
  public ResponseEntity<PullRequestStateReconciliationResultDto> reconcilePullRequestState(
      @PathVariable Long repositoryId,
      @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) throws IOException {
    if (!repositoryAuthorizationService.hasAdminAccess(repositoryId)) {
      throw new AccessDeniedException(
          "You do not have admin access for repository ID: " + repositoryId);
    }

    return ResponseEntity.ok(
        pullRequestStateReconciliationService.reconcilePullRequestState(repositoryId, dryRun));
  }
}
