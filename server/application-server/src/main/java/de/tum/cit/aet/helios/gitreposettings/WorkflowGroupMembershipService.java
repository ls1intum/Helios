package de.tum.cit.aet.helios.gitreposettings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class WorkflowGroupMembershipService {

  private final WorkflowGroupMembershipRepository workflowGroupMembershipRepository;

  @Transactional
  public void deleteAllMembershipsForRepository(Long repositoryId) {
    workflowGroupMembershipRepository.deleteAllByRepositoryId(repositoryId);
  }
}
