package de.tum.cit.aet.helios.workflow;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    public WorkflowService(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    public Optional<WorkflowDTO> getWorkflowById(Long id) {
        return workflowRepository.findById(id)
                .map(WorkflowDTO::fromWorkflow);
    }

    public List<WorkflowDTO> getAllWorkflows() {
        return workflowRepository.findAll().stream()
                .map(WorkflowDTO::fromWorkflow)
                .collect(Collectors.toList());
    }

    public List<WorkflowDTO> getWorkflowsByRepositoryId(Long repositoryId) {
        return workflowRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId)
                .stream()
                .map(WorkflowDTO::fromWorkflow)
                .collect(Collectors.toList());
    }

    public List<WorkflowDTO> getWorkflowsByState(Workflow.State state) {
        return workflowRepository.findByStateOrderByCreatedAtDesc(state)
                .stream()
                .map(WorkflowDTO::fromWorkflow)
                .collect(Collectors.toList());
    }
}
