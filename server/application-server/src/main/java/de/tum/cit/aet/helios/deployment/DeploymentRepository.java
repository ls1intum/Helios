package de.tum.cit.aet.helios.deployment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
  List<Deployment> findByEnvironmentEntityIdOrderByCreatedAtDesc(Long environmentId);

  Optional<Deployment> findFirstByEnvironmentEntityIdOrderByCreatedAtDesc(Long environmentId);
}
