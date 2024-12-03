package de.tum.cit.aet.helios.pullrequest;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.workflow.WorkflowRun;

@Entity
@DiscriminatorValue(value = "PULL_REQUEST")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequest extends Issue {

    private OffsetDateTime mergedAt;

    private String mergeCommitSha;

    private String headSha;

    private boolean isDraft;

    private boolean isMerged;

    private Boolean isMergeable;

    private String mergeableState;

    // Indicates whether maintainers can modify the pull request.
    private boolean maintainerCanModify;
    
    private int commits;
    
    private int additions;
    
    private int deletions;

    private int changedFiles;

    @ManyToOne
    @JoinColumn(name = "merged_by_id")
    @ToString.Exclude
    private User mergedBy;

    @ManyToMany
    @JoinTable(
        name = "pull_request_requested_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private Set<User> requestedReviewers = new HashSet<>();

    @Override
    public boolean isPullRequest() {
        return true;
    }

    @ManyToMany
    private Set<WorkflowRun> workflowRuns;

    // Missing properties:
    // - PullRequestReview
    // - PullRequestReviewComment


    // Ignored GitHub properties:
    // - rebaseable (not provided by our GitHub API client)
    // - head -> "label", "ref", "repo", "sha", "user"
    // - base -> "label", "ref", "repo", "sha", "user"
    // - auto_merge
    // - requested_teams
    // - comments (cached number)
    // - review_comments (cached number)
}