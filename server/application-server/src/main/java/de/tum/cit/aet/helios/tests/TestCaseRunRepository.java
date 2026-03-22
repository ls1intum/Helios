package de.tum.cit.aet.helios.tests;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRunRepository extends JpaRepository<TestCaseRun, Long> {
  @Query(
      "SELECT tcr FROM TestCaseRun tcr "
          + "JOIN FETCH tcr.testSuiteRun tsr "
          + "JOIN FETCH tcr.testCase tc "
          + "WHERE tsr.workflowRun.id = :workflowRunId "
          + "AND tsr.name IN :suiteNames "
          + "AND tsr.testType.id = :testTypeId")
  List<TestCaseRun> findByWorkflowRunIdAndSuiteNamesAndTestTypeId(
      @Param("workflowRunId") Long workflowRunId,
      @Param("suiteNames") Collection<String> suiteNames,
      @Param("testTypeId") Long testTypeId);

  @Query(
      "SELECT tcr FROM TestCaseRun tcr "
          + "JOIN FETCH tcr.testSuiteRun tsr "
          + "JOIN FETCH tcr.testCase tc "
          + "WHERE tsr.workflowRun.id = :workflowRunId "
          + "AND tsr.name IN :suiteNames "
          + "AND tsr.testType.id = :testTypeId "
          + "AND (tcr.status = 'FAILED' OR tcr.status = 'ERROR')")
  List<TestCaseRun> findFailedByWorkflowRunIdAndSuiteNamesAndTestTypeId(
      @Param("workflowRunId") Long workflowRunId,
      @Param("suiteNames") Collection<String> suiteNames,
      @Param("testTypeId") Long testTypeId);
}
