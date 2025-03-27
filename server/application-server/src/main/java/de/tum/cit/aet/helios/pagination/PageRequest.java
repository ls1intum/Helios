package de.tum.cit.aet.helios.pagination;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageRequest {
  private int page = 1;
  private int size = 20;
  private String sortField;
  private String sortDirection;
  private Map<String, String> filters = new HashMap<>();
}