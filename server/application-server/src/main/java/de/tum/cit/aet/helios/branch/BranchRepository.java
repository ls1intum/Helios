package de.tum.cit.aet.helios.branch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<Branch, BranchId> {
  Optional<Branch> findByName(String name);

  Optional<Branch> findByNameAndRepositoryRepositoryId(String name, Long repositoryId);

  void deleteByNameAndRepositoryRepositoryId(String name, Long repositoryId);

  List<Branch> findByRepositoryRepositoryId(Long repositoryId);

  Optional<Branch> findByRepositoryRepositoryIdAndName(Long repositoryId, String name);
}
