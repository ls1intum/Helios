package de.tum.cit.aet.helios.tests;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSuiteRunRepository extends JpaRepository<TestSuiteRun, Long> {

  /**
   * Retrieves test suite runs for a specific workflow run and test type, ordered by significance.
   *
   * <p>The ordering is determined by three criteria (in order of priority): 1. Test suites
   * containing test cases whose status changed compared to the previous run appear first 2. Test
   * suites are then ordered by the total number of failures and errors (descending) 3. Finally,
   * test suites are ordered alphabetically by name
   *
   * @param workflowRunId The ID of the current workflow run
   * @param testTypeId The ID of the test type to filter by
   * @param pageable Pagination information
   * @return A page of TestSuite objects meeting the specified criteria and ordering
   */
  @Query(
      """
      SELECT tsr
      FROM TestSuiteRun tsr
      WHERE tsr.workflowRun.id = :workflowRunId
      AND tsr.testType.id = :testTypeId
      AND (:search IS NULL OR EXISTS (
          SELECT 1
          FROM TestCaseRun tcr
          JOIN tcr.testCase tc
          WHERE tcr.testSuiteRun = tsr
          AND (LOWER(tc.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(tc.className) LIKE LOWER(CONCAT('%', :search, '%')))
      ))
      AND (:onlyFailed = false OR tsr.failures > 0 OR tsr.errors > 0)
      ORDER BY
          (tsr.failures + tsr.errors) DESC,
          tsr.name ASC
      """)
  Page<TestSuiteRun> findByWorkflowRunIdAndTestTypeId(
      @Param("workflowRunId") long workflowRunId,
      @Param("testTypeId") long testTypeId,
      @Param("search") String search,
      @Param("onlyFailed") boolean onlyFailed,
      Pageable pageable);

  /**
   * Retrieves a summary of test suite run results for a specific workflow run and test type. The
   * summary includes aggregated test statistics and change detection compared to a previous run.
   *
   * @param workflowRunId The ID of the current workflow run
   * @param testTypeId The ID of the test type to filter results
   * @return TestSuiteSummaryDto containing some aggregated test statistics and change detection
   *     information
   */
  @Query(
      """
          SELECT new de.tum.cit.aet.helios.tests.TestSuiteSummaryDto(
              SUM(tsr.tests),
              SUM(tsr.failures),
              SUM(tsr.errors),
              SUM(tsr.skipped),
              SUM(tsr.time),
              FALSE
          )
          FROM TestSuiteRun tsr
          WHERE tsr.workflowRun.id = :workflowRunId
          AND tsr.testType.id = :testTypeId
      """)
  TestSuiteSummaryDto findSummaryByWorkflowRunIdAndTestTypeId(
      @Param("workflowRunId") long workflowRunId,
      @Param("testTypeId") long testTypeId);
}
