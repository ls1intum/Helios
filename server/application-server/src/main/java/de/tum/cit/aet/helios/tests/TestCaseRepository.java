package de.tum.cit.aet.helios.tests;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
  @Query(
      "SELECT tc FROM TestCase tc "
          + "JOIN FETCH tc.testSuite ts "
          + "WHERE ts.workflowRun.id = :workflowRunId "
          + "AND ts.name IN :classNames "
          + "AND ts.testType.id = :testTypeId")
  List<TestCase> findByTestSuiteWorkflowIdAndClassNamesAndTestTypeId(
      @Param("workflowRunId") Long workflowId,
      @Param("classNames") Collection<String> classNames,
      @Param("testTypeId") Long testTypeId);

  @Query(
      "SELECT tc FROM TestCase tc "
          + "JOIN FETCH tc.testSuite ts "
          + "WHERE ts.workflowRun.id = :workflowRunId "
          + "AND ts.name IN :classNames "
          + "AND ts.testType.id = :testTypeId "
          + "AND (tc.status = 'FAILED' OR tc.status = 'ERROR')")
  List<TestCase> findFailedByTestSuiteWorkflowIdAndClassNamesAndTestTypeId(
      @Param("workflowRunId") Long workflowId,
      @Param("classNames") Collection<String> classNames,
      @Param("testTypeId") Long testTypeId);

  @Query(
      """
          SELECT tc
          FROM TestCase tc
          JOIN FETCH tc.testSuite ts
          JOIN FETCH ts.workflowRun wr
          WHERE tc.id = :testCaseId
            AND wr.repository.repositoryId = :repositoryId
          """)
  Optional<TestCase> findForTestFailureAnalysisByTestCaseId(
      @Param("repositoryId") Long repositoryId, @Param("testCaseId") Long testCaseId);
}
