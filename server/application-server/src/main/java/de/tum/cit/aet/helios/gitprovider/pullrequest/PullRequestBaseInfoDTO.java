package de.tum.cit.aet.helios.gitprovider.pullrequest;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.gitprovider.issue.Issue;
import de.tum.cit.aet.helios.gitprovider.issue.Issue.State;
import de.tum.cit.aet.helios.gitprovider.repository.RepositoryInfoDTO;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestBaseInfoDTO(
        @NonNull Long id,
        @NonNull Integer number,
        @NonNull String title,
        @NonNull State state,
        @NonNull Boolean isDraft,
        @NonNull Boolean isMerged,
        RepositoryInfoDTO repository,
        @NonNull String htmlUrl) {

    public static PullRequestBaseInfoDTO fromPullRequest(PullRequest pullRequest) {
        return new PullRequestBaseInfoDTO(
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getTitle(),
                pullRequest.getState(),
                pullRequest.isDraft(),
                pullRequest.isMerged(),
                RepositoryInfoDTO.fromRepository(pullRequest.getRepository()),
                pullRequest.getHtmlUrl());
    }

    public static PullRequestBaseInfoDTO fromIssue(Issue issue) {
        return new PullRequestBaseInfoDTO(
                issue.getId(),
                issue.getNumber(),
                issue.getTitle(),
                issue.getState(),
                false,
                false,
                RepositoryInfoDTO.fromRepository(issue.getRepository()),
                issue.getHtmlUrl());
    }
}