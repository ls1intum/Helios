package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
  List<Environment> findByRepository(GitRepository repository);

  Environment findByNameAndRepository(String environmentName, GitRepository repository);

  List<Environment> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
