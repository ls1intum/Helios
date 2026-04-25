package de.tum.cit.aet.helios.ai.testfailure;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestFailureAnalysisRepository extends JpaRepository<TestFailureAnalysis, Long> {

  @SuppressWarnings({"checkstyle:MethodName", "checkstyle:LineLength"})
  Optional<TestFailureAnalysis>
      findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
          Long testCaseId, String providerId, TestFailureAnalysisStatus status);

  long countByRequesterUserIdAndCreatedAtAfter(String requesterUserId, OffsetDateTime cutoff);

  @SuppressWarnings("checkstyle:MethodName")
  Optional<TestFailureAnalysis> findFirstByRequesterUserIdAndCreatedAtAfterOrderByCreatedAtAsc(
      String requesterUserId, OffsetDateTime cutoff);

  long countByUpdatedAtBefore(OffsetDateTime cutoff);

  long deleteByUpdatedAtBefore(OffsetDateTime cutoff);
}
