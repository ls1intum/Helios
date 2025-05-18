package de.tum.cit.aet.helios.pullrequest.pagination;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PullRequestPageRequest {
  private int page = 1;
  private int size = 20;
  private String sortField;
  private String sortDirection;
  private PullRequestFilterType filterType = PullRequestFilterType.OPEN;
  private String searchTerm;
}
