package de.tum.cit.aet.helios.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowDTO(
    @NonNull Long id,
    RepositoryInfoDTO repository,
    @NonNull String name,
    @NonNull String path,
    String fileNameWithExtension,
    @NonNull Workflow.State state,
    String url,
    String htmlUrl,
    String badgeUrl,
    @NonNull Workflow.Label label,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static WorkflowDTO fromWorkflow(Workflow workflow) {
    return new WorkflowDTO(
        workflow.getId(),
        RepositoryInfoDTO.fromRepository(workflow.getRepository()),
        workflow.getName(),
        workflow.getPath(),
        workflow.getFileNameWithExtension(),
        workflow.getState(),
        workflow.getUrl(),
        workflow.getHtmlUrl(),
        workflow.getBadgeUrl(),
        workflow.getLabel(),
        workflow.getCreatedAt(),
        workflow.getUpdatedAt());
  }
}
