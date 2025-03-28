package de.tum.cit.aet.helios.gitrepo;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfoDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String nameWithOwner,
    String description,
    @NonNull String htmlUrl,
    @NonNull OffsetDateTime updatedAt,
    int stargazersCount,
    int pullRequestCount,
    int branchCount,
    int environmentCount,
    String latestReleaseTagName,
    List<UserInfoDto> contributors) {

  public static RepositoryInfoDto fromRepository(GitRepository repository) {
    return new RepositoryInfoDto(
        repository.getRepositoryId(),
        repository.getName(),
        repository.getNameWithOwner(),
        repository.getDescription(),
        repository.getHtmlUrl(),
        repository.getUpdatedAt(),
        repository.getStargazersCount(),
        repository.getPullRequestCount(),
        repository.getBranchCount(),
        repository.getEnvironmentCount(),
        repository.getLatestReleaseTagName(),
        repository.getContributors().stream().map(user -> UserInfoDto.fromUser(user)).toList());
  }
}
