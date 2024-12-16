package de.tum.cit.aet.helios.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import de.tum.cit.aet.helios.issue.Issue.State;
import de.tum.cit.aet.helios.user.UserInfoDTO;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestInfoDTO(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull String title,
    @NonNull State state,
    @NonNull Boolean isDraft,
    @NonNull Boolean isMerged,
    @NonNull Integer commentsCount,
    UserInfoDTO author,
    List<UserInfoDTO> assignees,
    RepositoryInfoDTO repository,
    @NonNull Integer additions,
    @NonNull Integer deletions,
    @NonNull String headSha,
    OffsetDateTime mergedAt,
    OffsetDateTime closedAt,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static PullRequestInfoDTO fromPullRequest(PullRequest pullRequest) {
    return new PullRequestInfoDTO(
        pullRequest.getId(),
        pullRequest.getNumber(),
        pullRequest.getTitle(),
        pullRequest.getState(),
        pullRequest.isDraft(),
        pullRequest.isMerged(),
        pullRequest.getCommentsCount(),
        UserInfoDTO.fromUser(pullRequest.getAuthor()),
        pullRequest.getAssignees().stream()
            .map(UserInfoDTO::fromUser)
            .sorted(Comparator.comparing(UserInfoDTO::login))
            .toList(),
        RepositoryInfoDTO.fromRepository(pullRequest.getRepository()),
        pullRequest.getAdditions(),
        pullRequest.getDeletions(),
        pullRequest.getHeadSha(),
        pullRequest.getMergedAt(),
        pullRequest.getClosedAt(),
        pullRequest.getHtmlUrl(),
        pullRequest.getCreatedAt(),
        pullRequest.getUpdatedAt());
  }
}
