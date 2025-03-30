package de.tum.cit.aet.helios.tests.type;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestTypeRepository extends JpaRepository<TestType, Long> {
  List<TestType> findAllByRepositoryRepositoryId(Long repositoryId);

  Optional<TestType> findByIdAndRepositoryRepositoryId(Long id, Long repositoryId);

  boolean existsByNameAndRepositoryRepositoryIdAndIdNot(String name, Long repositoryId, Long id);

  boolean existsByNameAndRepositoryRepositoryId(String name, Long repositoryId);
}
