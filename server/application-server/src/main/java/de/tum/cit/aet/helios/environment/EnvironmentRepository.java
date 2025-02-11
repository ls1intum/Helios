package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
  List<Environment> findAllByOrderByNameAsc();

  List<Environment> findByRepository(GitRepository repository);

  Environment findByNameAndRepository(String environmentName, GitRepository repository);

  List<Environment> findByRepositoryRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

  List<Environment> findByEnabledTrueOrderByNameAsc();

  @Query("SELECT DISTINCT e FROM Environment e " +
      "LEFT JOIN FETCH e.statusHistory es " + //
      "WHERE (es is NULL OR es.checkTimestamp = " +
      "(SELECT MAX(es2.checkTimestamp) FROM EnvironmentStatus es2 WHERE es2.environment = e))" +
      "AND e.statusCheckType IS NOT NULL")
  List<Environment> findByStatusCheckTypeIsNotNullWithLatestStatus();
}
