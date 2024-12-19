package de.tum.cit.aet.helios.gitreposettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface GitRepoSettingsRepository extends JpaRepository<GitRepoSettings, Long> {

    Optional<GitRepoSettings> findByRepositoryId(Long repositoryId);
}