package de.tum.cit.aet.helios.workflow;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<List<WorkflowDTO>> getAllWorkflows() {
        List<WorkflowDTO> workflows = workflowService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDTO> getWorkflowById(@PathVariable Long id) {
        Optional<WorkflowDTO> workflow = workflowService.getWorkflowById(id);
        return workflow.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{workflowId}/label")
    public ResponseEntity<Void> updateWorkflowLabel(
            @PathVariable Long workflowId,
            @RequestBody Workflow.Label label
    ) {
        workflowService.updateWorkflowLabel(workflowId, label);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<List<WorkflowDTO>> getWorkflowsByRepositoryId(@PathVariable Long repositoryId) {
        List<WorkflowDTO> workflows = workflowService.getWorkflowsByRepositoryId(repositoryId);
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/state/{state}")
    public ResponseEntity<List<WorkflowDTO>> getWorkflowsByState(@PathVariable Workflow.State state) {
        List<WorkflowDTO> workflows = workflowService.getWorkflowsByState(state);
        return ResponseEntity.ok(workflows);
    }
}
