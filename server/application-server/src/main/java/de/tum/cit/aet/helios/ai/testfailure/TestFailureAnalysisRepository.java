package de.tum.cit.aet.helios.ai.testfailure;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestFailureAnalysisRepository extends JpaRepository<TestFailureAnalysis, Long> {

  @SuppressWarnings("checkstyle:MethodName")
  Optional<TestFailureAnalysis> findFirstByTestCase_IdAndProviderIdOrderByAnalyzedAtDescIdDesc(
      Long testCaseId, String providerId);

  long countByAnalyzedAtBefore(OffsetDateTime cutoff);

  long deleteByAnalyzedAtBefore(OffsetDateTime cutoff);
}
