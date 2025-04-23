package de.tum.cit.aet.helios.releaseinfo.release;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRepository extends JpaRepository<Release, Long> {
  Optional<Release> findByTagNameAndRepositoryRepositoryId(String tagName, Long repositoryId);
}
