package de.tum.cit.aet.helios.tests.pagination;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FlakyTestsPageRequest {
  @Builder.Default private int page = 1;
  @Builder.Default private int size = 20;
  private String sortDirection;
  @Builder.Default private FlakyTestsFilterType filterType = FlakyTestsFilterType.ALL;
  private String searchTerm;
}
