package de.tum.cit.aet.helios.tests;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRepository
    extends JpaRepository<TestCase, Long>, JpaSpecificationExecutor<TestCase> {

  Optional<TestCase> findByRepositoryRepositoryIdAndSuiteNameAndClassNameAndName(
      Long repositoryId, String suiteName, String className, String name);

  /**
   * Total number of flaky tests for a repository, regardless of flakiness score.
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
}
