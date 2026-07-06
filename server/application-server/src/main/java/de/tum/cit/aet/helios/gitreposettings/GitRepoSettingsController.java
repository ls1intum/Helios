package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigDto;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigService;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitreposettings.secret.RepoSecretService;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/settings/{repositoryId}")
public class GitRepoSettingsController {

  private final WorkflowGroupService workflowGroupService;
  private final GitRepoSettingsService gitRepoSettingsService;
  private final RepoSecretService secrets;
  private final DeploymentWorkflowConfigService deploymentWorkflowConfigService;
  private final PipelineConfigService pipelineConfigService;

  @GetMapping("/pipeline-config")
  public ResponseEntity<PipelineConfigDto> getPipelineConfig(@PathVariable Long repositoryId) {
    verifyRepositoryContext(repositoryId);
    return ResponseEntity.ok(pipelineConfigService.getConfig(repositoryId));
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/pipeline-config")
  public ResponseEntity<PipelineConfigDto> updatePipelineConfig(
      @PathVariable Long repositoryId, @Valid @RequestBody PipelineConfigDto config) {
    verifyRepositoryContext(repositoryId);
    return ResponseEntity.ok(pipelineConfigService.updateConfig(repositoryId, config));
  }

  @EnforceAtLeastMaintainer
  @GetMapping("/pipeline-config/suggestions")
  public ResponseEntity<PipelineConfigDto> getPipelineConfigSuggestions(
      @PathVariable Long repositoryId) {
    verifyRepositoryContext(repositoryId);
    return ResponseEntity.ok(pipelineConfigService.suggest(repositoryId));
  }

  /**
   * Rejects a request whose {@code X-REPOSITORY-ID} header (from which the maintainer role is
   * derived — see GitHubJwtAuthenticationConverter) targets a different repository than the path.
   * Without this, a maintainer of repo A could act on repo B by keeping the header on A. Skipped
   * when no header is present (the role check then already fails for maintainer endpoints).
   *
   * <p>NOTE: the sibling maintainer endpoints in this controller (groups/settings/secret) share the
   * same header-vs-path decoupling; applying this guard to them centrally is a recommended
   * follow-up.
   */
  private void verifyRepositoryContext(Long repositoryId) {
    final Long contextRepositoryId = RepositoryContext.getRepositoryId();
    if (contextRepositoryId != null && !contextRepositoryId.equals(repositoryId)) {
      throw new AccessDeniedException(
          "Repository mismatch: the request's repository context does not match the path.");
    }
  }

  @GetMapping("/settings")
  public ResponseEntity<GitRepoSettingsDto> getGitRepoSettings(@PathVariable Long repositoryId) {
    GitRepoSettingsDto gitRepoSettingsDto =
        gitRepoSettingsService.getGitRepoSettingsByRepositoryId(repositoryId);
    return ResponseEntity.ok(gitRepoSettingsDto);
  }

  @GetMapping("/groups")
  public ResponseEntity<List<WorkflowGroupDto>> getGroupsWithWorkflows(
      @PathVariable Long repositoryId) {
    List<WorkflowGroupDto> workflowGroupDtoS =
        workflowGroupService.getAllWorkflowGroupsByRepositoryId(repositoryId);
    return ResponseEntity.ok(workflowGroupDtoS);
  }

  @EnforceAtLeastMaintainer
  @PostMapping("/groups/create")
  public ResponseEntity<WorkflowGroupDto> createWorkflowGroup(
      @PathVariable Long repositoryId, @Valid @RequestBody WorkflowGroupDto workflowGroupDto) {
    WorkflowGroupDto createdWorkflowGroup =
        workflowGroupService.createWorkflowGroup(repositoryId, workflowGroupDto);
    return ResponseEntity.ok(createdWorkflowGroup);
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/groups/update")
  public ResponseEntity<Void> updateWorkflowGroups(
      @PathVariable Long repositoryId, @Valid @RequestBody List<WorkflowGroupDto> workflowGroups) {
    workflowGroupService.updateWorkflowGroups(repositoryId, workflowGroups);
    return ResponseEntity.noContent().build();
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/settings")
  public ResponseEntity<?> updateGitRepoSettings(
      @PathVariable Long repositoryId, @Valid @RequestBody GitRepoSettingsDto gitRepoSettingsDto) {
    Optional<GitRepoSettingsDto> updateGitRepoSettings =
        gitRepoSettingsService.updateGitRepoSettings(repositoryId, gitRepoSettingsDto);
    return updateGitRepoSettings.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastMaintainer
  @DeleteMapping("/groups/{groupId}")
  public ResponseEntity<Void> deleteWorkflowGroup(
      @PathVariable Long repositoryId, @PathVariable Long groupId) {
    workflowGroupService.deleteWorkflowGroup(groupId, repositoryId);
    return ResponseEntity.noContent().build();
  }

  @EnforceAtLeastMaintainer
  @PostMapping("/secret")
  public ResponseEntity<String> rotateSecret(@PathVariable Long repositoryId) {
    String token = secrets.rotate(repositoryId);
    return ResponseEntity.ok(token);
  }

  @GetMapping("/workflows/{workflowId}/deployment-config")
  public ResponseEntity<DeploymentWorkflowConfigDto> getDeploymentWorkflowConfig(
      @PathVariable Long repositoryId, @PathVariable Long workflowId) {
    return deploymentWorkflowConfigService
        .findByWorkflowId(repositoryId, workflowId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/workflows/{workflowId}/deployment-config")
  public ResponseEntity<DeploymentWorkflowConfigDto> upsertDeploymentWorkflowConfig(
      @PathVariable Long repositoryId,
      @PathVariable Long workflowId,
      @RequestBody DeploymentWorkflowConfigDto dto) {
    DeploymentWorkflowConfigDto result =
        deploymentWorkflowConfigService.upsert(repositoryId, workflowId, dto);
    return ResponseEntity.ok(result);
  }
}
