package de.tum.cit.aet.helios.gitrepo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfoDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String nameWithOwner,
    String description,
    @NonNull String htmlUrl) {

  public static RepositoryInfoDto fromRepository(GitRepository repository) {
    return new RepositoryInfoDto(
        repository.getRepositoryId(),
        repository.getName(),
        repository.getNameWithOwner(),
        repository.getDescription(),
        repository.getHtmlUrl());
  }
}
