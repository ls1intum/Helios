package de.tum.cit.aet.helios.workflow.pipeline.config;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineCategoryRepository extends JpaRepository<PipelineCategory, Long> {

  /** Ordered categories for a repository (via its repository_settings). Nodes load lazily. */
  @Query(
      "SELECT c FROM PipelineCategory c "
          + "WHERE c.gitRepoSettings.repository.repositoryId = :repositoryId "
          + "ORDER BY c.orderIndex ASC")
  List<PipelineCategory> findByRepositoryIdOrdered(@Param("repositoryId") Long repositoryId);
}
