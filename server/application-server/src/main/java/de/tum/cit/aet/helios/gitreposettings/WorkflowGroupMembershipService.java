package de.tum.cit.aet.helios.gitreposettings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowGroupMembershipService {

  private final WorkflowGroupMembershipRepository workflowGroupMembershipRepository;

  public WorkflowGroupMembershipService(
      WorkflowGroupMembershipRepository workflowGroupMembershipRepository) {
    this.workflowGroupMembershipRepository = workflowGroupMembershipRepository;
  }

  @Transactional
  public void deleteAllMembershipsForRepository(Long repositoryId) {
    workflowGroupMembershipRepository.deleteAllByRepositoryId(repositoryId);
  }
}
