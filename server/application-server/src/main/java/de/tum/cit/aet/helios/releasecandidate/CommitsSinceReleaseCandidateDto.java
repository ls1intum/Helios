package de.tum.cit.aet.helios.releasecandidate;

import java.util.List;
import org.springframework.lang.NonNull;

public record CommitsSinceReleaseCandidateDto(
    @NonNull Integer aheadBy,
    @NonNull Integer behindBy,
    @NonNull List<CompareCommitInfoDto> commits,
    String compareUrl) {
  public static record CompareCommitInfoDto(
      @NonNull String sha,
      @NonNull String message,
      @NonNull String authorName,
      @NonNull String authorEmail,
      @NonNull String url) {}
}
