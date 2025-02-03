package de.tum.cit.aet.helios.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagEvaluationRepository extends JpaRepository<TagEvaluation, TagEvaluationId> {
  List<TagEvaluation> findByTag(Tag tag);
  Optional<TagEvaluation> findByTagAndEvaluatedById(Tag tag, Long userId);
}
