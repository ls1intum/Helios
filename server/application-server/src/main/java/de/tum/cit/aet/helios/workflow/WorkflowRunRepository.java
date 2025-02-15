package de.tum.cit.aet.helios.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
  List<WorkflowRun> findByPullRequestsIdAndHeadSha(Long pullRequestsId, String headSha);

  List<WorkflowRun> findByHeadBranchAndHeadShaAndPullRequestsIsNull(String branch, String headSha);

  WorkflowRun findFirstByHeadBranchAndHeadShaAndWorkflowIdOrderByCreatedAtDesc(
      String branch, String headSha, Long workflowId);
}
