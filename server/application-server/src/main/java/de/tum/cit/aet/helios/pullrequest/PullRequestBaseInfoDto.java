package de.tum.cit.aet.helios.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.issue.Issue.State;
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
    @NonNull String htmlUrl) {

  public static PullRequestBaseInfoDto fromPullRequest(PullRequest pullRequest) {
    return new PullRequestBaseInfoDto(
        pullRequest.getId(),
        pullRequest.getNumber(),
        pullRequest.getTitle(),
        pullRequest.getState(),
        pullRequest.isDraft(),
        pullRequest.isMerged(),
        RepositoryInfoDto.fromRepository(pullRequest.getRepository()),
        pullRequest.getHtmlUrl());
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
        issue.getHtmlUrl());
  }
}
