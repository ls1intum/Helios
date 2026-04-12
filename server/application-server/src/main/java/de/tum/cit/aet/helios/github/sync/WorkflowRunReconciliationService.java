package de.tum.cit.aet.helios.github.sync;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import de.tum.cit.aet.helios.workflow.github.GitHubWorkflowRunStateMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class WorkflowRunReconciliationService {

  private static final long STALE_AFTER_MINUTES = 60L;
  private static final int BATCH_SIZE = 100;

  private static final List<WorkflowRun.Status> INCOMPLETE_WORKFLOW_STATUSES =
      List.of(
          WorkflowRun.Status.QUEUED,
          WorkflowRun.Status.IN_PROGRESS,
          WorkflowRun.Status.PENDING,
          WorkflowRun.Status.WAITING,
          WorkflowRun.Status.REQUESTED,
          WorkflowRun.Status.ACTION_REQUIRED);

  @Value("${reconciliation.enabled:true}")
  private boolean enabled;

  private final WorkflowRunRepository workflowRunRepository;
  private final GitHubService gitHubService;

  @Scheduled(cron = "${reconciliation.workflow-runs.cron:0 7/15 * * * *}")
  public void reconcileStaleWorkflowRuns() {
    if (!enabled) {
      log.debug("Workflow run reconciliation is disabled.");
      return;
    }

    OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
    int processed = 0;
    int updated = 0;
    int pages = 0;
    OffsetDateTime cursorTime = null;
    long cursorId = 0L;

    while (true) {
      List<WorkflowRun> staleWorkflowRuns =
          workflowRunRepository.findStaleIncompleteRuns(
              threshold,
              INCOMPLETE_WORKFLOW_STATUSES,
              cursorTime,
              cursorId,
              PageRequest.of(0, BATCH_SIZE));

      if (staleWorkflowRuns.isEmpty()) {
        break;
      }

      pages++;
      processed += staleWorkflowRuns.size();
      WorkflowRun lastInBatch = staleWorkflowRuns.getLast();
      OffsetDateTime nextCursorTime = getSortKey(lastInBatch);
      long nextCursorId = lastInBatch.getId();

      for (WorkflowRun workflowRun : staleWorkflowRuns) {
        String repositoryNameWithOwner =
            resolveRepositoryNameWithOwner(workflowRun.getRepository());
        if (repositoryNameWithOwner == null) {
          log.warn(
              "Skipping stale workflow run {} because repository is missing.", workflowRun.getId());
          continue;
        }

        try {
          Optional<GitHubService.WorkflowRunState> remoteState =
              gitHubService.getWorkflowRunState(repositoryNameWithOwner, workflowRun.getId());
          if (remoteState.isEmpty()) {
            continue;
          }

          if (applyWorkflowRunState(workflowRun, remoteState.get())) {
            updated++;
          }
        } catch (IOException ex) {
          log.warn(
              "Failed to reconcile workflow run {} in repository {}: {}",
              workflowRun.getId(),
              repositoryNameWithOwner,
              ex.getMessage());
        }
      }

      cursorTime = nextCursorTime;
      cursorId = nextCursorId;
    }

    log.info(
        "Workflow run reconciliation finished. Processed {} stale workflow run(s) across {}"
            + " page(s); updated {} workflow run(s).",
        processed,
        pages,
        updated);
  }

  private boolean applyWorkflowRunState(
      WorkflowRun workflowRun, GitHubService.WorkflowRunState remoteState) {
    WorkflowRun.Status mappedStatus = GitHubWorkflowRunStateMapper.mapStatus(remoteState.status());
    if (mappedStatus == null) {
      return false;
    }

    WorkflowRun.Conclusion mappedConclusion =
        GitHubWorkflowRunStateMapper.mapConclusion(remoteState.conclusion());
    OffsetDateTime remoteUpdatedAt = remoteState.updatedAt();

    WorkflowRun.Conclusion currentConclusion = workflowRun.getConclusion().orElse(null);
    boolean statusChanged =
        workflowRun.getStatus() != mappedStatus
            || !Objects.equals(currentConclusion, mappedConclusion);
    boolean timestampAdvanced = isRemoteTimestampNewer(remoteUpdatedAt, workflowRun.getUpdatedAt());

    if (!statusChanged && !timestampAdvanced) {
      return false;
    }

    if (statusChanged) {
      workflowRun.setStatus(mappedStatus);
      workflowRun.setConclusion(Optional.ofNullable(mappedConclusion));
    }

    if (timestampAdvanced) {
      workflowRun.setUpdatedAt(remoteUpdatedAt);
    }
    workflowRunRepository.save(workflowRun);
    return true;
  }

  private boolean isRemoteTimestampNewer(OffsetDateTime remote, OffsetDateTime local) {
    return remote != null && (local == null || remote.isAfter(local));
  }

  private OffsetDateTime getSortKey(WorkflowRun workflowRun) {
    return workflowRun.getUpdatedAt() != null
        ? workflowRun.getUpdatedAt() : workflowRun.getCreatedAt();
  }

  private String resolveRepositoryNameWithOwner(GitRepository repository) {
    return repository == null ? null : repository.getNameWithOwner();
  }
}
