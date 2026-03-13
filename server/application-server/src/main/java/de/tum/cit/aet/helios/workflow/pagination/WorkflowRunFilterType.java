package de.tum.cit.aet.helios.workflow.pagination;

public enum WorkflowRunFilterType {
  ALL,
  NOT_STARTED,
  IN_PROGRESS,
  CANCELLED,
  SUCCESS,
  FAILURE,
  ACTION_REQUIRED;

  public static WorkflowRunFilterType from(String value) {
    if (value == null) {
      return ALL;
    }

    try {
      return WorkflowRunFilterType.valueOf(value);
    } catch (IllegalArgumentException e) {
      return ALL;
    }
  }
}
