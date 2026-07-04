package de.tum.cit.aet.helios.workflow.queue.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;

/** DTOs for the queue + runner controllers. Plan §D. */
public final class QueueDtos {

  private QueueDtos() {}

  public record QueueDepthDto(
      List<LabelSetDepth> labelSets, int totalQueued, int totalInProgress) {}

  public record LabelSetDepth(
      List<String> labels,
      int queued,
      int inProgress,
      Long oldestQueuedSeconds,
      String runnerKind) {}

  public record QueuedJobDto(
      Long jobId,
      Long runId,
      String workflowName,
      String jobName,
      String headBranch,
      List<String> labels,
      Integer waitSeconds,
      Long etaSeconds,
      Integer positionInQueue,
      String queuedReason,
      boolean isStuck,
      String runnerKind) {}

  public record QueueStatsDto(
      int samples,
      Integer queueP50,
      Integer queueP90,
      Integer queueP95,
      Integer runP50,
      Integer runP90,
      Integer runP95,
      List<TrendPoint> trend) {}

  public record TrendPoint(
      OffsetDateTime bucket, Integer queueP50, Integer runP50) {}

  public record RunnerDto(
      Long id,
      String name,
      String os,
      String status,
      boolean busy,
      List<String> labels,
      Long runnerGroupId,
      String runnerGroupName,
      Long currentJobId,
      OffsetDateTime lastSeenAt,
      OffsetDateTime offlineSince) {}

  public record RunnerPoolDto(
      List<String> labels, int online, int busy, int idle, int offline) {}

  public record AlertRuleDto(
      Long id,
      @NotNull @Pattern(regexp = "QUEUE_P95_OVER|RUNNER_OFFLINE_OVER|STUCK_JOBS_OVER")
          String kind,
      @NotNull @Min(0) Integer thresholdSeconds,
      @Min(1) Integer windowMinutes,
      Long repositoryId,
      String labelSetHash,
      List<String> channels,
      boolean enabled,
      @Pattern(
              regexp = "^$|^([01][0-9]|2[0-3]):[0-5][0-9]-([01][0-9]|2[0-3]):[0-5][0-9]$",
              message = "quietWindow must be HH:mm-HH:mm")
          String quietWindow) {}

  public record AlertEventDto(
      Long id,
      Long ruleId,
      Long repositoryId,
      OffsetDateTime firedAt,
      OffsetDateTime clearedAt,
      Integer measuredValue,
      String details) {}
}
