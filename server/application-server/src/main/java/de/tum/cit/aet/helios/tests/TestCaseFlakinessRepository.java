package de.tum.cit.aet.helios.tests;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseFlakinessRepository
    extends JpaRepository<TestCaseFlakiness, Long>, JpaSpecificationExecutor<TestCaseFlakiness> {

  /**
   * Total number of flaky tests tracked for a repository.
   */
  long countByRepositoryRepositoryId(Long repositoryId);

  /**
   * Number of flaky tests with flakiness score strictly greater than {@code minScore}.
   * Use {@code minScore = 70.0} to count high-flakiness tests.
   */
  long countByRepositoryRepositoryIdAndFlakinessScoreGreaterThan(
      Long repositoryId, double minScore);

  /**
   * Number of flaky tests with flakiness score in the range ({@code minScore}, {@code maxScore}].
   * Use {@code minScore = 30.0, maxScore = 70.0} to count medium-flakiness tests.
   */
  long countByRepositoryRepositoryIdAndFlakinessScoreGreaterThanAndFlakinessScoreLessThanEqual(
      Long repositoryId, double minScore, double maxScore);

  /**
   * Finds the existing flakiness record for a specific test in a repository, if any.
   */
  Optional<TestCaseFlakiness>
      findByTestNameAndClassNameAndTestSuiteNameAndRepositoryRepositoryId(
          String testName, String className, String testSuiteName, Long repositoryId);

  /**
   * Finds flakiness records matching a test name and class name for a repository, ordered by
   * flakiness score descending. Used by the CI per-test flakiness endpoint where the test suite
   * name is not known.
   */
  @Query(
      "SELECT t FROM TestCaseFlakiness t"
          + " WHERE t.repository.repositoryId = :repositoryId"
          + "   AND t.testName = :testName"
          + "   AND t.className = :className"
          + " ORDER BY t.flakinessScore DESC")
  List<TestCaseFlakiness> findByRepositoryIdAndTestNameAndClassName(
      @Param("repositoryId") Long repositoryId,
      @Param("testName") String testName,
      @Param("className") String className);
}
