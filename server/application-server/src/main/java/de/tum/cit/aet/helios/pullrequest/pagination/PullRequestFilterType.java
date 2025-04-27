package de.tum.cit.aet.helios.pullrequest.pagination;

public enum PullRequestFilterType {
  ALL,
  OPEN,
  OPEN_READY_FOR_REVIEW,
  DRAFT,
  MERGED,
  CLOSED,
  USER_AUTHORED,
  ASSIGNED_TO_USER,
  REVIEW_REQUESTED;

  public static PullRequestFilterType fromString(String value) {
    if (value == null) {
      return ALL;
    }

    try {
      return PullRequestFilterType.valueOf(value);
    } catch (IllegalArgumentException e) {
      return ALL;
    }
  }
}
