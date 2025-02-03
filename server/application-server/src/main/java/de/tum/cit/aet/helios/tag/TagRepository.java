package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, TagId> {
  List<Tag> findAllByOrderByNameAsc();

  List<Tag> findByRepository(GitRepository repository);

  Optional<Tag> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<Tag> findByRepositoryRepositoryIdAndCommitSha(Long repositoryId, String commitSha);

  boolean existsByRepositoryRepositoryIdAndName(Long repositoryId, String name);
}
