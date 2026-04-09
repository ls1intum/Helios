package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHWorkflowRun;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class GitHubWorkflowRunConverter
    extends BaseGitServiceEntityConverter<GHWorkflowRun, WorkflowRun> {

  private static final int MAX_NAME_AND_TITLE_LENGTH = 255;

  @Override
  public WorkflowRun convert(@NonNull GHWorkflowRun source) {
    return update(source, new WorkflowRun());
  }

  @Override
  public WorkflowRun update(@NonNull GHWorkflowRun source, @NonNull WorkflowRun workflowRun) {
    convertBaseFields(source, workflowRun);
    workflowRun.setName(
        truncate(source.getName(), MAX_NAME_AND_TITLE_LENGTH, "name", source.getId()));
    workflowRun.setDisplayTitle(
        truncate(workflowRun.getDisplayTitle(), MAX_NAME_AND_TITLE_LENGTH, "displayTitle",
            source.getId()));
    workflowRun.setRunNumber(source.getRunNumber());
    workflowRun.setRunAttempt(source.getRunAttempt());
    try {
      workflowRun.setRunStartedAt(DateUtil.convertToOffsetDateTime(source.getRunStartedAt()));
    } catch (IOException e) {
      log.error(
          "Failed to convert runStartedAt field for source {}: {}", source.getId(), e.getMessage());
    }
    try {
      workflowRun.setHtmlUrl(source.getHtmlUrl().toString());
    } catch (IOException e) {
      log.error(
          "Failed to convert htmlUrl field for source {}: {}", source.getId(), e.getMessage());
    }
    try {
      workflowRun.setHtmlUrl(source.getHtmlUrl().toString());
    } catch (IOException e) {
      log.error(
          "Failed to convert htmlUrl field for source {}: {}", source.getId(), e.getMessage());
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
    workflowRun.setStatus(GitHubWorkflowRunStateMapper.mapStatus(source.getStatus()));
    workflowRun.setConclusion(
        Optional.ofNullable(source.getConclusion())
            .map(GitHubWorkflowRunStateMapper::mapConclusion));

    return workflowRun;
  }

  private static String truncate(String value, int maxLength, String fieldName, long sourceId) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    String truncatedValue = value.substring(0, maxLength);
    log.info(
        "Truncated workflow run field '{}' from {} to {} characters for source {}: '{}'",
        fieldName,
        value.length(),
        maxLength,
        sourceId,
        truncatedValue);
    return truncatedValue;
  }
}
