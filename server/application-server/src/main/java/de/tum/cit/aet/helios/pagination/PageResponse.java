package de.tum.cit.aet.helios.pagination;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageResponse<T> {
  private List<T> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
}