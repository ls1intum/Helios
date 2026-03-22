package de.tum.cit.aet.helios.tests;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for test case statistics. Provides methods to find and manage statistics for test
 * cases.
 */
@Repository
public interface TestCaseStatisticsRepository extends JpaRepository<TestCaseStatistics, Long> {

  /**
   * Find statistics for a specific test case on a specific branch.
   *
   * @param testCaseId the ID of the test case
   * @param branchName the branch name
   * @return the statistics if found
   */
  Optional<TestCaseStatistics> findByTestCaseIdAndBranchName(Long testCaseId, String branchName);

  /**
   * Find all statistics for test cases on a branch in a repository.
   *
   * @param testCaseIds the collection of test case IDs
   * @param branchName the branch name
   * @return list of statistics for matching test cases
   */
  List<TestCaseStatistics> findByTestCaseIdInAndBranchName(
      Collection<Long> testCaseIds, String branchName);
}
