package de.tum.cit.aet.helios.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagEvaluationRepository extends JpaRepository<TagEvaluation, Long> {}
