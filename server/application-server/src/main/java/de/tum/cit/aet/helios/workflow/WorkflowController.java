package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

  private final WorkflowService workflowService;

  @GetMapping
  public ResponseEntity<List<WorkflowDto>> getAllWorkflows() {
    List<WorkflowDto> workflows = workflowService.getAllWorkflows();
    return ResponseEntity.ok(workflows);
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowDto> getWorkflowById(@PathVariable Long id) {
    Optional<WorkflowDto> workflow = workflowService.getWorkflowById(id);
    return workflow.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/{workflowId}/label")
  public ResponseEntity<Void> updateWorkflowLabel(
      @PathVariable Long workflowId, @RequestBody Workflow.Label label) {
    workflowService.updateWorkflowLabel(workflowId, label);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/repository/{repositoryId}")
  public ResponseEntity<List<WorkflowDto>> getWorkflowsByRepositoryId(
      @PathVariable Long repositoryId) {
    List<WorkflowDto> workflows = workflowService.getWorkflowsByRepositoryId(repositoryId);
    return ResponseEntity.ok(workflows);
  }

  @GetMapping("/state/{state}")
  public ResponseEntity<List<WorkflowDto>> getWorkflowsByState(@PathVariable Workflow.State state) {
    List<WorkflowDto> workflows = workflowService.getWorkflowsByState(state);
    return ResponseEntity.ok(workflows);
  }
}
