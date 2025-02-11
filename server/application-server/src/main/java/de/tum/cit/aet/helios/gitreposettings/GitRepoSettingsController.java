package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
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
@RestController
@RequestMapping("/api/settings/{repositoryId}")
public class GitRepoSettingsController {

  private final WorkflowGroupService workflowGroupService;
  private final GitRepoSettingsService gitRepoSettingsService;

  public GitRepoSettingsController(
      WorkflowGroupService workflowGroupService, GitRepoSettingsService gitRepoSettingsService) {
    this.workflowGroupService = workflowGroupService;
    this.gitRepoSettingsService = gitRepoSettingsService;
  }

  @GetMapping("/settings")
  @EnforceAtLeastMaintainer
  public ResponseEntity<GitRepoSettingsDto> getGitRepoSettings(@PathVariable Long repositoryId) {
    GitRepoSettingsDto gitRepoSettingsDto =
        gitRepoSettingsService.getOrCreateGitRepoSettingsByRepositoryId(repositoryId).orElseThrow();
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
}
