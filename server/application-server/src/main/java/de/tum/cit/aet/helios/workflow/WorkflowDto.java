package de.tum.cit.aet.helios.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowDto(
    @NonNull Long id,
    RepositoryInfoDto repository,
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

  public static WorkflowDto fromWorkflow(Workflow workflow) {
    return new WorkflowDto(
        workflow.getId(),
        RepositoryInfoDto.fromRepository(workflow.getRepository()),
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
