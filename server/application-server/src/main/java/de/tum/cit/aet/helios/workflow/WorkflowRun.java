package de.tum.cit.aet.helios.workflow;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "workflow_run")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class WorkflowRun extends BaseGitServiceEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepository repository;

    @ManyToMany
    private Set<PullRequest> pullRequests;

    private String name;

    private String displayTitle;

    private long runNumber;

    private long workflowId;

    private long runAttempt;

    private OffsetDateTime runStartedAt;

    private String htmlUrl;

    private String jobsUrl;

    private String logsUrl;

    private String checkSuiteUrl;

    private String artifactsUrl;

    private String cancelUrl;

    private String rerunUrl;

    private String workflowUrl;

    private String headBranch;

    private String headSha;

    @NonNull
    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Conclusion conclusion;

    public Optional<Conclusion> getConclusion() {
        return Optional.ofNullable(conclusion);
    }
    
    public void setConclusion(Optional<Conclusion> conclusion) {
        this.conclusion = conclusion.orElse(null);
    }

    public enum Status {
        /** The queued. */
        QUEUED,
        /** The in progress. */
        IN_PROGRESS,
        /** The completed. */
        COMPLETED,
        /** The action required. */
        ACTION_REQUIRED,
        /** The cancelled. */
        CANCELLED,
        /** The failure. */
        FAILURE,
        /** The neutral. */
        NEUTRAL,
        /** The skipped. */
        SKIPPED,
        /** The stale. */
        STALE,
        /** The success. */
        SUCCESS,
        /** The timed out. */
        TIMED_OUT,
        /** The requested. */
        REQUESTED,
        /** The waiting. */
        WAITING,
        /** The pending. */
        PENDING,
        /** The unknown. */
        UNKNOWN;
    }

    public enum Conclusion {
        /** The action required. */
        ACTION_REQUIRED,
        /** The cancelled. */
        CANCELLED,
        /** The failure. */
        FAILURE,
        /** The neutral. */
        NEUTRAL,
        /** The success. */
        SUCCESS,
        /** The skipped. */
        SKIPPED,
        /** The stale. */
        STALE,
        /** The timed out. */
        TIMED_OUT,
        /** Start up fail */
        STARTUP_FAILURE,
        /** The unknown. */
        UNKNOWN;
    }

    // Missing:
    // - event

}