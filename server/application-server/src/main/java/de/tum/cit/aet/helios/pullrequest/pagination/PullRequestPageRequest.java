package de.tum.cit.aet.helios.pullrequest.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PullRequestPageRequest {
  @Schema(defaultValue = "1")
  private int page = 1;

  @Schema(defaultValue = "20")
  private int size = 20;

  private String sortField;
  private String sortDirection;

  @Schema(defaultValue = "OPEN")
  private PullRequestFilterType filterType = PullRequestFilterType.OPEN;

  private String searchTerm;
  private String author;
  private String assignee;
  private Boolean noAssignee;
  private Long labelId;
  private Boolean noLabel;
  private PullRequestReviewFilterType reviewState;
  private String requestedReviewer;
}
