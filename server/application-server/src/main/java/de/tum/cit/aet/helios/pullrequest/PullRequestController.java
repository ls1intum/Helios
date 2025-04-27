package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.pagination.PageResponse;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestFilterType;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

  @GetMapping("/paginated")
  public ResponseEntity<PageResponse<PullRequestBaseInfoDto>> getPaginatedPullRequests(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sortField,
      @RequestParam(required = false) String sortDirection,
      @RequestParam(required = false) PullRequestFilterType filterType,
      @RequestParam(required = false) String searchTerm) {

    // Create a request object with type-safe parameters
    PullRequestPageRequest pageRequest = new PullRequestPageRequest();
    pageRequest.setPage(page);
    pageRequest.setSize(size);
    pageRequest.setSortField(sortField);
    pageRequest.setSortDirection(sortDirection);
    pageRequest.setFilterType(filterType != null ? filterType : PullRequestFilterType.ALL);
    pageRequest.setSearchTerm(searchTerm);

    System.out.println("PageRequest: " + pageRequest);
    System.out.println("PageRequest page: " + pageRequest.getPage());
    System.out.println("PageRequest size: " + pageRequest.getSize());
    System.out.println("PageRequest sortField: " + pageRequest.getSortField());
    System.out.println("PageRequest sortDirection: " + pageRequest.getSortDirection());
    System.out.println("PageRequest filterType: " + pageRequest.getFilterType());
    System.out.println("PageRequest searchTerm: " + pageRequest.getSearchTerm());


    PageResponse<PullRequestBaseInfoDto> response =
        pullRequestService.getPaginatedPullRequests(pageRequest);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<PullRequestBaseInfoDto>> getAllPullRequests() {
    List<PullRequestBaseInfoDto> pullRequests = pullRequestService.getAllPullRequests();
    return ResponseEntity.ok(pullRequests);
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

  @PostMapping("/{pr}/pin")
  public ResponseEntity<Void> setPrPinnedByNumber(
      @PathVariable Long pr, @RequestParam(name = "isPinned") Boolean isPinned) {
    pullRequestService.setPrPinnedByNumberAndUserId(pr, isPinned);
    return ResponseEntity.ok().build();
  }
}
