package de.tum.cit.aet.helios.gitreposettings;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/settings/{repositoryId}")
public class GitRepoSettingsController {

  private final WorkflowGroupService workflowGroupService;

  public GitRepoSettingsController(WorkflowGroupService workflowGroupService) {
    this.workflowGroupService = workflowGroupService;
  }

  @GetMapping("/groups")
  public ResponseEntity<List<WorkflowGroupDTO>> getGroupsWithWorkflows(
      @PathVariable Long repositoryId) {
    List<WorkflowGroupDTO> workflowGroupDTOS =
        workflowGroupService.getAllWorkflowGroupsByRepositoryId(repositoryId);
    return ResponseEntity.ok(workflowGroupDTOS);
  }

  @PostMapping("/groups/create")
  public ResponseEntity<WorkflowGroupDTO> createWorkflowGroup(
      @PathVariable Long repositoryId, @Valid @RequestBody WorkflowGroupDTO workflowGroupDTO) {
    WorkflowGroupDTO createdWorkflowGroup =
        workflowGroupService.createWorkflowGroup(repositoryId, workflowGroupDTO);
    return ResponseEntity.ok(createdWorkflowGroup);
  }

  @PutMapping("/groups/update")
  public ResponseEntity<Void> updateWorkflowGroups(
      @PathVariable Long repositoryId, @Valid @RequestBody List<WorkflowGroupDTO> workflowGroups) {
    workflowGroupService.updateWorkflowGroups(repositoryId, workflowGroups);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/groups/{groupId}")
  public ResponseEntity<Void> deleteWorkflowGroup(
      @PathVariable Long repositoryId, @PathVariable Long groupId) {
    workflowGroupService.deleteWorkflowGroup(groupId, repositoryId);
    return ResponseEntity.noContent().build();
  }
}
