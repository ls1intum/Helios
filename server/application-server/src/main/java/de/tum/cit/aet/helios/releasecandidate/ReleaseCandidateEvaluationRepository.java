package de.tum.cit.aet.helios.releasecandidate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseCandidateEvaluationRepository
    extends JpaRepository<ReleaseCandidateEvaluation, ReleaseCandidateEvaluationId> {
  List<ReleaseCandidateEvaluation> findByReleaseCandidate(ReleaseCandidate releaseCandidate);

  Optional<ReleaseCandidateEvaluation> findByReleaseCandidateAndEvaluatedById(
      ReleaseCandidate releaseCandidate, Long userId);
}
