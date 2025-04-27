package de.tum.cit.aet.helios.pullrequest.pagination;

import de.tum.cit.aet.helios.pullrequest.PullRequestBaseInfoDto;
import java.util.List;

public record PaginatedPullRequestsResponse(
    // always shown, not paged
    List<PullRequestBaseInfoDto> pinned,
    // paged, non-pinned pull requests
    List<PullRequestBaseInfoDto> page,
    int pageNumber,
    int pageSize,
    long totalNonPinned,
    int totalPages) {
}