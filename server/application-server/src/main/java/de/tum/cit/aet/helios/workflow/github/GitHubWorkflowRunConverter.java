package de.tum.cit.aet.helios.workflow.github;

import java.io.IOException;
import java.util.Optional;

import org.kohsuke.github.GHWorkflowRun;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class GitHubWorkflowRunConverter extends BaseGitServiceEntityConverter<GHWorkflowRun, WorkflowRun> {

    @Override
    public WorkflowRun convert(@NonNull GHWorkflowRun source) {
        return update(source, new WorkflowRun());
    }

    @Override
    public WorkflowRun update(@NonNull GHWorkflowRun source, @NonNull WorkflowRun workflowRun) {
        convertBaseFields(source, workflowRun);
        workflowRun.setName(source.getName());
        workflowRun.setDisplayTitle(source.getDisplayTitle());
        workflowRun.setRunNumber(source.getRunNumber());
        workflowRun.setWorkflowId(source.getWorkflowId());
        workflowRun.setRunAttempt(source.getRunAttempt());
        try {
            workflowRun.setRunStartedAt(DateUtil.convertToOffsetDateTime(source.getRunStartedAt()));
        } catch (IOException e) {
            log.error("Failed to convert runStartedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            workflowRun.setHtmlUrl(source.getHtmlUrl().toString());
        } catch (IOException e) {
            log.error("Failed to convert htmlUrl field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            workflowRun.setHtmlUrl(source.getHtmlUrl().toString());
        } catch (IOException e) {
            log.error("Failed to convert htmlUrl field for source {}: {}", source.getId(), e.getMessage());
        }
        workflowRun.setJobsUrl(source.getJobsUrl().toString());
        workflowRun.setLogsUrl(source.getLogsUrl().toString());
        workflowRun.setCheckSuiteUrl(source.getCheckSuiteUrl().toString());
        workflowRun.setArtifactsUrl(source.getArtifactsUrl().toString());
        workflowRun.setCancelUrl(source.getCancelUrl().toString());
        workflowRun.setRerunUrl(source.getRerunUrl().toString());
        workflowRun.setWorkflowUrl(source.getWorkflowUrl().toString());
        workflowRun.setHeadBranch(source.getHeadBranch().toString());
        workflowRun.setHeadSha(source.getHeadSha());
        workflowRun.setStatus(convertStatus(source.getStatus()));
        workflowRun.setConclusion(Optional.ofNullable(source.getConclusion()).map(this::convertConclusion));

        return workflowRun;
    }

    private WorkflowRun.Status convertStatus(GHWorkflowRun.Status status) {
        switch (status) {
            case ACTION_REQUIRED:
                return WorkflowRun.Status.ACTION_REQUIRED;
            case COMPLETED:
                return WorkflowRun.Status.COMPLETED;
            case IN_PROGRESS:
                return WorkflowRun.Status.IN_PROGRESS;
            case QUEUED:
                return WorkflowRun.Status.QUEUED;
            case REQUESTED:
                return WorkflowRun.Status.REQUESTED;
            case CANCELLED:
                return WorkflowRun.Status.CANCELLED;
            case FAILURE:
                return WorkflowRun.Status.FAILURE;
            case NEUTRAL:
                return WorkflowRun.Status.NEUTRAL;
            case PENDING:
                return WorkflowRun.Status.PENDING;
            case SKIPPED:
                return WorkflowRun.Status.SKIPPED;
            case STALE:
                return WorkflowRun.Status.STALE;
            case SUCCESS:
                return WorkflowRun.Status.SUCCESS;
            case TIMED_OUT:
                return WorkflowRun.Status.TIMED_OUT;
            case WAITING:
                return WorkflowRun.Status.WAITING;
                default:
            case UNKNOWN:
                return WorkflowRun.Status.UNKNOWN;
        }
    }

    private WorkflowRun.Conclusion convertConclusion(GHWorkflowRun.Conclusion conclusion) {
        switch (conclusion) {
            case ACTION_REQUIRED:
                return WorkflowRun.Conclusion.ACTION_REQUIRED;
            case CANCELLED:
                return WorkflowRun.Conclusion.CANCELLED;
            case FAILURE:
                return WorkflowRun.Conclusion.FAILURE;
            case NEUTRAL:
                return WorkflowRun.Conclusion.NEUTRAL;
            case SKIPPED:
                return WorkflowRun.Conclusion.SKIPPED;
            case STALE:
                return WorkflowRun.Conclusion.STALE;
            case STARTUP_FAILURE:
                return WorkflowRun.Conclusion.STARTUP_FAILURE;
            case SUCCESS:
                return WorkflowRun.Conclusion.SUCCESS;
            case TIMED_OUT:
                return WorkflowRun.Conclusion.TIMED_OUT;
            default:
            case UNKNOWN:
                return WorkflowRun.Conclusion.UNKNOWN;
        }
    }

}
