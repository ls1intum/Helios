package de.tum.cit.aet.helios.gitreposettings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitRepoSettingsRepository extends JpaRepository<GitRepoSettings, Long> {

  Optional<GitRepoSettings> findByRepositoryId(Long repositoryId);
}
