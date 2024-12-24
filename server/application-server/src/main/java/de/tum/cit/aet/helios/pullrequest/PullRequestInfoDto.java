package de.tum.cit.aet.helios.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.issue.Issue.State;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestInfoDto(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull String title,
    @NonNull State state,
    @NonNull Boolean isDraft,
    @NonNull Boolean isMerged,
    @NonNull Integer commentsCount,
    UserInfoDto author,
    List<UserInfoDto> assignees,
    RepositoryInfoDto repository,
    @NonNull Integer additions,
    @NonNull Integer deletions,
    @NonNull String headSha,
    @NonNull String headRefName,
    @NonNull String headRefRepoNameWithOwner,
    OffsetDateTime mergedAt,
    OffsetDateTime closedAt,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static PullRequestInfoDto fromPullRequest(PullRequest pullRequest) {
    return new PullRequestInfoDto(
        pullRequest.getId(),
        pullRequest.getNumber(),
        pullRequest.getTitle(),
        pullRequest.getState(),
        pullRequest.isDraft(),
        pullRequest.isMerged(),
        pullRequest.getCommentsCount(),
        UserInfoDto.fromUser(pullRequest.getAuthor()),
        pullRequest.getAssignees().stream()
            .map(UserInfoDto::fromUser)
            .sorted(Comparator.comparing(UserInfoDto::login))
            .toList(),
        RepositoryInfoDto.fromRepository(pullRequest.getRepository()),
        pullRequest.getAdditions(),
        pullRequest.getDeletions(),
        pullRequest.getHeadSha(),
        pullRequest.getHeadRefName(),
        pullRequest.getHeadRefRepoNameWithOwner(),
        pullRequest.getMergedAt(),
        pullRequest.getClosedAt(),
        pullRequest.getHtmlUrl(),
        pullRequest.getCreatedAt(),
        pullRequest.getUpdatedAt());
  }
}
