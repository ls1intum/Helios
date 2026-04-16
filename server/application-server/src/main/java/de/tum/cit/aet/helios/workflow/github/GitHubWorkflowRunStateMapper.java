package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.util.Locale;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.util.StringUtils;

public final class GitHubWorkflowRunStateMapper {

  private GitHubWorkflowRunStateMapper() {}

  public static WorkflowRun.Status mapStatus(GHWorkflowRun.Status status) {
    if (status == null) {
      return WorkflowRun.Status.UNKNOWN;
    }

    return switch (status) {
      case ACTION_REQUIRED -> WorkflowRun.Status.ACTION_REQUIRED;
      case COMPLETED -> WorkflowRun.Status.COMPLETED;
      case IN_PROGRESS -> WorkflowRun.Status.IN_PROGRESS;
      case QUEUED -> WorkflowRun.Status.QUEUED;
      case REQUESTED -> WorkflowRun.Status.REQUESTED;
      case CANCELLED -> WorkflowRun.Status.CANCELLED;
      case FAILURE -> WorkflowRun.Status.FAILURE;
      case NEUTRAL -> WorkflowRun.Status.NEUTRAL;
      case PENDING -> WorkflowRun.Status.PENDING;
      case SKIPPED -> WorkflowRun.Status.SKIPPED;
      case STALE -> WorkflowRun.Status.STALE;
      case SUCCESS -> WorkflowRun.Status.SUCCESS;
      case TIMED_OUT -> WorkflowRun.Status.TIMED_OUT;
      case WAITING -> WorkflowRun.Status.WAITING;
      case UNKNOWN -> WorkflowRun.Status.UNKNOWN;
    };
  }

  public static WorkflowRun.Status mapStatus(String rawStatus) {
    if (!StringUtils.hasText(rawStatus)) {
      return null;
    }

    return switch (rawStatus.trim().toUpperCase(Locale.ROOT)) {
      case "ACTION_REQUIRED" -> WorkflowRun.Status.ACTION_REQUIRED;
      case "COMPLETED" -> WorkflowRun.Status.COMPLETED;
      case "IN_PROGRESS" -> WorkflowRun.Status.IN_PROGRESS;
      case "QUEUED" -> WorkflowRun.Status.QUEUED;
      case "REQUESTED" -> WorkflowRun.Status.REQUESTED;
      case "CANCELLED" -> WorkflowRun.Status.CANCELLED;
      case "FAILURE" -> WorkflowRun.Status.FAILURE;
      case "NEUTRAL" -> WorkflowRun.Status.NEUTRAL;
      case "PENDING" -> WorkflowRun.Status.PENDING;
      case "SKIPPED" -> WorkflowRun.Status.SKIPPED;
      case "STALE" -> WorkflowRun.Status.STALE;
      case "SUCCESS" -> WorkflowRun.Status.SUCCESS;
      case "TIMED_OUT" -> WorkflowRun.Status.TIMED_OUT;
      case "WAITING" -> WorkflowRun.Status.WAITING;
      default -> WorkflowRun.Status.UNKNOWN;
    };
  }

  public static WorkflowRun.Conclusion mapConclusion(GHWorkflowRun.Conclusion conclusion) {
    if (conclusion == null) {
      return null;
    }

    return switch (conclusion) {
      case ACTION_REQUIRED -> WorkflowRun.Conclusion.ACTION_REQUIRED;
      case CANCELLED -> WorkflowRun.Conclusion.CANCELLED;
      case FAILURE -> WorkflowRun.Conclusion.FAILURE;
      case NEUTRAL -> WorkflowRun.Conclusion.NEUTRAL;
      case SKIPPED -> WorkflowRun.Conclusion.SKIPPED;
      case STALE -> WorkflowRun.Conclusion.STALE;
      case STARTUP_FAILURE -> WorkflowRun.Conclusion.STARTUP_FAILURE;
      case SUCCESS -> WorkflowRun.Conclusion.SUCCESS;
      case TIMED_OUT -> WorkflowRun.Conclusion.TIMED_OUT;
      case UNKNOWN -> WorkflowRun.Conclusion.UNKNOWN;
    };
  }

  public static WorkflowRun.Conclusion mapConclusion(String rawConclusion) {
    if (!StringUtils.hasText(rawConclusion)) {
      return null;
    }

    return switch (rawConclusion.trim().toUpperCase(Locale.ROOT)) {
      case "ACTION_REQUIRED" -> WorkflowRun.Conclusion.ACTION_REQUIRED;
      case "CANCELLED" -> WorkflowRun.Conclusion.CANCELLED;
      case "FAILURE" -> WorkflowRun.Conclusion.FAILURE;
      case "NEUTRAL" -> WorkflowRun.Conclusion.NEUTRAL;
      case "SKIPPED" -> WorkflowRun.Conclusion.SKIPPED;
      case "STALE" -> WorkflowRun.Conclusion.STALE;
      case "STARTUP_FAILURE" -> WorkflowRun.Conclusion.STARTUP_FAILURE;
      case "SUCCESS" -> WorkflowRun.Conclusion.SUCCESS;
      case "TIMED_OUT" -> WorkflowRun.Conclusion.TIMED_OUT;
      default -> WorkflowRun.Conclusion.UNKNOWN;
    };
  }

}
