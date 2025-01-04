package de.tum.cit.aet.helios.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.issue.Issue.State;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestBaseInfoDto(
    @NonNull Long id,
    @NonNull Integer number,
    @NonNull String title,
    @NonNull State state,
    @NonNull Boolean isDraft,
    @NonNull Boolean isMerged,
    RepositoryInfoDto repository,
    @NonNull String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    UserInfoDto author,
    List<UserInfoDto> assignees,
    List<UserInfoDto> reviewers
) {

  public static PullRequestBaseInfoDto fromPullRequest(PullRequest pullRequest) {
    return new PullRequestBaseInfoDto(
        pullRequest.getId(),
        pullRequest.getNumber(),
        pullRequest.getTitle(),
        pullRequest.getState(),
        pullRequest.isDraft(),
        pullRequest.isMerged(),
        RepositoryInfoDto.fromRepository(pullRequest.getRepository()),
        pullRequest.getHtmlUrl(),
        pullRequest.getCreatedAt(),
        pullRequest.getUpdatedAt(),
        UserInfoDto.fromUser(pullRequest.getAuthor()),
        pullRequest.getAssignees().stream()
            .map(UserInfoDto::fromUser)
            .sorted(Comparator.comparing(UserInfoDto::login))
            .toList(),
        pullRequest.getRequestedReviewers().stream()
            .map(UserInfoDto::fromUser)
            .sorted(Comparator.comparing(UserInfoDto::login))
            .toList());
  }

  public static PullRequestBaseInfoDto fromIssue(Issue issue) {
    return new PullRequestBaseInfoDto(
        issue.getId(),
        issue.getNumber(),
        issue.getTitle(),
        issue.getState(),
        false,
        false,
        RepositoryInfoDto.fromRepository(issue.getRepository()),
        issue.getHtmlUrl(),
        issue.getCreatedAt(),
        issue.getUpdatedAt(),
        UserInfoDto.fromUser(issue.getAuthor()),
        issue.getAssignees().stream()
            .map(UserInfoDto::fromUser)
            .sorted(Comparator.comparing(UserInfoDto::login))
            .toList(),
        new ArrayList<>() 
        );
  }
}
