package de.tum.cit.aet.helios.tests;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
  List<TestSuite> findByWorkflowRunId(long workflowRunId);

  /**
   * Retrieves test suites for a specific workflow run and test type, ordered by significance.
   *
   * <p>The ordering is determined by three criteria (in order of priority): 1. Test suites
   * containing test cases whose status changed compared to the previous run appear first 2. Test
   * suites are then ordered by the total number of failures and errors (descending) 3. Finally,
   * test suites are ordered alphabetically by name
   *
   * @param workflowRunId The ID of the current workflow run
   * @param testTypeId The ID of the test type to filter by
   * @param prevWorkflowRunId The ID of the previous workflow run for status change comparison. This
   *     can be null if no previous run is available.
   * @param pageable Pagination information
   * @return A page of TestSuite objects meeting the specified criteria and ordering
   */
  @Query(
      """
      SELECT ts
      FROM TestSuite ts
      WHERE ts.workflowRun.id = :workflowRunId
      AND ts.testType.id = :testTypeId
      AND (:search IS NULL OR EXISTS (
          SELECT 1
          FROM TestCase tc
          WHERE tc.testSuite = ts
          AND (LOWER(tc.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(tc.className) LIKE LOWER(CONCAT('%', :search, '%')))
      ))
      AND (:onlyFailed = false OR ts.failures > 0 OR ts.errors > 0)
      ORDER BY
          (ts.failures + ts.errors) DESC,
          ts.name ASC
      """)
  Page<TestSuite> findByWorkflowRunIdAndTestTypeId(
      @Param("workflowRunId") long workflowRunId,
      @Param("testTypeId") long testTypeId,
      @Param("prevWorkflowRunId") Long prevWorkflowRunId,
      @Param("search") String search,
      @Param("onlyFailed") boolean onlyFailed,
      Pageable pageable);

  /**
   * Retrieves a summary of test suite results for a specific workflow run and test type. The
   * summary includes aggregated test statistics and change detection compared to a previous run.
   *
   * @param workflowRunId The ID of the current workflow run
   * @param testTypeId The ID of the test type to filter results
   * @param prevWorkflowRunId The ID of the previous workflow run for comparison (can be null)
   * @return TestSuiteSummaryDto containing some aggregated test statistics and change detection
   *     information
   */
  @Query(
      """
          SELECT new de.tum.cit.aet.helios.tests.TestSuiteSummaryDto(
              SUM(ts.tests),
              SUM(ts.failures),
              SUM(ts.errors),
              SUM(ts.skipped),
              SUM(ts.time),
              FALSE
          )
          FROM TestSuite ts
          WHERE ts.workflowRun.id = :workflowRunId
          AND ts.testType.id = :testTypeId
      """)
  TestSuiteSummaryDto findSummaryByWorkflowRunIdAndTestTypeId(
      @Param("workflowRunId") long workflowRunId,
      @Param("testTypeId") long testTypeId,
      @Param("prevWorkflowRunId") Long prevWorkflowRunId);
}
