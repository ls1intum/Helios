package de.tum.cit.aet.helios.releaseinfo.release;

import org.springframework.lang.NonNull;

public record ReleaseDto(
    @NonNull boolean isDraft,
    @NonNull boolean isPrerelease,
    @NonNull String body,
    @NonNull String githubUrl) {
  public static ReleaseDto fromRelease(@NonNull Release release) {
    return new ReleaseDto(
        release.isDraft(), release.isPrerelease(), release.getBody(), release.getGithubUrl());
  }
}
