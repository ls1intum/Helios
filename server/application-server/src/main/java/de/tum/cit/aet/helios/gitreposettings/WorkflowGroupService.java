package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowDTO;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkflowGroupService {

    private final WorkflowGroupRepository workflowGroupRepository;
    private final GitRepoSettingsRepository gitRepoSettingsRepository;
    private final GitRepoRepository gitRepoRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowGroupMembershipRepository workflowGroupMembershipRepository;

    public WorkflowGroupService(WorkflowGroupRepository workflowGroupRepository, GitRepoSettingsRepository gitRepoSettingsRepository, GitRepoRepository gitRepoRepository, WorkflowRepository workflowRepository, WorkflowGroupMembershipRepository workflowGroupMembershipRepository) {
        this.workflowGroupRepository = workflowGroupRepository;
        this.gitRepoSettingsRepository = gitRepoSettingsRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.workflowRepository = workflowRepository;
        this.workflowGroupMembershipRepository = workflowGroupMembershipRepository;
    }

    @Transactional
    public WorkflowGroupDTO createWorkflowGroup(Long repositoryId, WorkflowGroupDTO workflowGroupDTO) {
        // Validate input, check if name or orderIndex is empty
        if (workflowGroupDTO.name() == null || workflowGroupDTO.name().isEmpty()) {
            throw new IllegalArgumentException("Name or orderIndex is empty while creating workflow group.");
        }

        GitRepoSettings gitRepoSettings = getOrCreateGitRepoSettings(repositoryId);

        // Get the latest orderIndex for the repository
        Integer latestOrderIndex = workflowGroupRepository.findLatestOrderIndexByGitRepoSettings(gitRepoSettings)
                .orElse(-1);

        // Create new group and save it
        WorkflowGroup workflowGroup = new WorkflowGroup();
        workflowGroup.setName(workflowGroupDTO.name());
        workflowGroup.setGitRepoSettings(gitRepoSettings);
        workflowGroup.setOrderIndex(latestOrderIndex + 1);
        return WorkflowGroupDTO.fromWorkflowGroup(workflowGroupRepository.save(workflowGroup));
    }

    @Transactional
    public void deleteWorkflowGroup(Long workflowGroupId, Long repositoryId) {
        // Find the WorkflowGroup by ID
        WorkflowGroup workflowGroup = workflowGroupRepository.findById(workflowGroupId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowGroup with id " + workflowGroupId + " not found."));

        // Ensure the WorkflowGroup belongs to the given repository
        if (!workflowGroup.getGitRepoSettings().getRepository().getId().equals(repositoryId)) {
            throw new IllegalArgumentException("WorkflowGroup does not belong to the specified repository.");
        }

        // Check if the WorkflowGroup is empty
        if (!workflowGroup.getMemberships().isEmpty()) {
            throw new IllegalStateException("WorkflowGroup cannot be deleted as it contains workflows.");
        }

        // Store the orderIndex of the WorkflowGroup being deleted
        int deletedOrderIndex = workflowGroup.getOrderIndex();

        // Delete the WorkflowGroup
        workflowGroupRepository.delete(workflowGroup);

        // Decrement the orderIndex of all WorkflowGroups with a higher orderIndex in the same repository
        workflowGroupRepository.decrementOrderIndexesAfter(deletedOrderIndex, workflowGroup.getGitRepoSettings());
    }

    public List<WorkflowGroupDTO> getAllWorkflowGroupsByRepositoryId(Long repositoryId) {
        return workflowGroupRepository.findAllByRepositoryId(repositoryId).stream()
                .map(WorkflowGroupDTO::fromWorkflowGroup)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateWorkflowGroups(Long repositoryId, List<WorkflowGroupDTO> workflowGroups) {
        GitRepoSettings gitRepoSettings = getOrCreateGitRepoSettings(repositoryId);

        // Fetch all existing groups for the repository
        List<WorkflowGroup> existingGroups = workflowGroupRepository.findAllByGitRepoSettings(gitRepoSettings);
        Map<Long, WorkflowGroup> groupMap = existingGroups.stream()
                .collect(Collectors.toMap(WorkflowGroup::getId, group -> group));

        // Validate all group IDs in the request
        Set<Long> providedGroupIds = workflowGroups.stream()
                .map(WorkflowGroupDTO::id)
                .collect(Collectors.toSet());

        if (!groupMap.keySet().containsAll(providedGroupIds)) {
            providedGroupIds.removeAll(groupMap.keySet());
            throw new IllegalArgumentException("The following groups do not exist: " + providedGroupIds);
        }

        // Validate all workflow IDs in the request
        Set<Long> providedWorkflowIds = workflowGroups.stream()
                .flatMap(group -> Optional.ofNullable(group.memberships())
                        .orElse(Collections.emptyList())
                        .stream())
                .map(WorkflowMembershipDTO::workflowId)
                .collect(Collectors.toSet());


        Set<Long> existingWorkflowIds = workflowRepository.findAllById(providedWorkflowIds).stream()
                .map(Workflow::getId)
                .collect(Collectors.toSet());

        if (!existingWorkflowIds.containsAll(providedWorkflowIds)) {
            providedWorkflowIds.removeAll(existingWorkflowIds);
            throw new IllegalArgumentException("The following workflows do not exist: " + providedWorkflowIds);
        }

        // Validate group orderIndex values
        List<Integer> groupOrderIndexes = workflowGroups.stream()
                .map(WorkflowGroupDTO::orderIndex)
                .sorted()
                .toList();

        if (!isValidOrderIndexSequence(groupOrderIndexes)) {
            throw new IllegalArgumentException("Group orderIndex values must start from 0 and be contiguous.");
        }

        // Validate workflow orderIndex values for each group
        for (WorkflowGroupDTO groupDTO : workflowGroups) {
            // Safely handle null memberships
            List<WorkflowMembershipDTO> memberships = groupDTO.memberships() != null
                    ? groupDTO.memberships()
                    : List.of();

            List<Integer> workflowOrderIndexes = memberships.stream()
                    .map(WorkflowMembershipDTO::orderIndex)
                    .sorted()
                    .toList();

            if (!isValidOrderIndexSequence(workflowOrderIndexes)) {
                throw new IllegalArgumentException(
                        "Workflow orderIndex values in group " + groupDTO.id() + " must start from 0 and be contiguous."
                );
            }
        }


        // Set temporary negative orderIndex values for all groups
        int tempOrderIndex = -100;
        for (WorkflowGroup group : existingGroups) {
            group.setOrderIndex(tempOrderIndex--);
        }
        workflowGroupRepository.saveAll(existingGroups);
        workflowGroupMembershipRepository.deleteAllByRepositoryId(repositoryId);
        // Flush to persist changes immediately
        workflowGroupRepository.flush();

        // Update groups and their memberships
        for (WorkflowGroupDTO groupDTO : workflowGroups) {
            WorkflowGroup group = groupMap.get(groupDTO.id());
            if (group == null) {
                throw new IllegalArgumentException("WorkflowGroup with id " + groupDTO.id() + " not found.");
            }

            // Update group meta
            group.setName(groupDTO.name());
            group.setOrderIndex(groupDTO.orderIndex());

            // Build final membership set
            Map<Long, WorkflowMembershipDTO> incoming = Optional.ofNullable(groupDTO.memberships())
                    .orElse(Collections.emptyList())
                    .stream()
                    .collect(Collectors.toMap(WorkflowMembershipDTO::workflowId, memDTO -> memDTO));

            // Remove old memberships not in incoming
            group.getMemberships().removeIf(existingMem ->
                    !incoming.containsKey(existingMem.getWorkflow().getId())
            );

            // For each new membership, either update or create
            for (WorkflowMembershipDTO memDTO : incoming.values()) {
                WorkflowGroupMembership membership = group.getMemberships().stream()
                        .filter(m -> m.getWorkflow().getId().equals(memDTO.workflowId()))
                        .findFirst()
                        .orElseGet(() -> {
                            // create a new membership
                            Workflow w = workflowRepository.findById(memDTO.workflowId())
                                    .orElseThrow(() -> new IllegalArgumentException("Not found"));
                            WorkflowGroupMembership newMem = new WorkflowGroupMembership();
                            newMem.setWorkflow(w);
                            newMem.setWorkflowGroup(group);
                            group.getMemberships().add(newMem);
                            return newMem;
                        });
                membership.setOrderIndex(memDTO.orderIndex());
            }
        }

        workflowGroupRepository.saveAll(existingGroups);
    }

    private GitRepoSettings getOrCreateGitRepoSettings(Long repositoryId) {
        GitRepository gitRepository = gitRepoRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository with id " + repositoryId + " not found while creating workflow group"));

        // Fetch the repository settings for the given repository ID
        return gitRepoSettingsRepository.findByRepositoryId(repositoryId)
                .orElseGet(() -> {
                    // If not, create a new one
                    GitRepoSettings newGitRepoSettings = new GitRepoSettings();
                    newGitRepoSettings.setRepository(gitRepository);
                    return gitRepoSettingsRepository.save(newGitRepoSettings);
                });
    }

    private boolean isValidOrderIndexSequence(List<Integer> orderIndexes) {
        if (orderIndexes.isEmpty()) {
            return true;
        }

        for (int i = 0; i < orderIndexes.size(); i++) {
            if (orderIndexes.get(i) != i) {
                return false;
            }
        }
        return true;
    }
}