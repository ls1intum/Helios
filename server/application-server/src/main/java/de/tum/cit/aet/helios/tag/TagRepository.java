package de.tum.cit.aet.helios.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, TagId> {
  List<Tag> findAllByOrderByNameAsc();

  Optional<Tag> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);

  Optional<Tag> findByRepositoryRepositoryIdAndCommitSha(Long repositoryId, String commitSha);

  boolean existsByRepositoryRepositoryIdAndName(Long repositoryId, String name);
}
