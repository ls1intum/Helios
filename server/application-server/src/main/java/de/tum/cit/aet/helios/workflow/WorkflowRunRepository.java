package de.tum.cit.aet.helios.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
  Optional<WorkflowRun> findById(long id);

  // This loads the test suites for the workflow run eagerly
  @Query(
      "SELECT DISTINCT wr FROM WorkflowRun wr "
          + "JOIN wr.pullRequests pr "
          + "LEFT JOIN FETCH wr.testSuites "
          + "WHERE pr.id = :pullRequestId "
          + "AND wr.headSha = :headSha")
  List<WorkflowRun> findByPullRequestsIdAndHeadShaWithTestSuites(
      Long pullRequestsId, String headSha);

  // This loads the test suites for the workflow run eagerly
  @Query(
      "SELECT DISTINCT wr FROM WorkflowRun wr "
          + "LEFT JOIN FETCH wr.testSuites "
          + "WHERE wr.headBranch = :branch "
          + "AND wr.headSha = :headSha "
          + "AND wr.pullRequests IS EMPTY")
  List<WorkflowRun> findByHeadBranchAndHeadShaAndPullRequestsIsNullWithTestSuites(
      String branch, String headSha);

  List<WorkflowRun> findByPullRequestsIdAndHeadSha(Long pullRequestsId, String headSha);

  List<WorkflowRun> findByHeadBranchAndHeadShaAndPullRequestsIsNull(String branch, String headSha);
}
