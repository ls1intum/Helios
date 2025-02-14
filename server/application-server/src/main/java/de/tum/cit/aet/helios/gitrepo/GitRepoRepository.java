package de.tum.cit.aet.helios.gitrepo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitRepoRepository extends JpaRepository<GitRepository, Long> {

  GitRepository findByNameWithOwner(String nameWithOwner);

  Optional<GitRepository> findByRepositoryId(Long repositoryId);

  void deleteByNameWithOwner(String nameWithOwner);
}
