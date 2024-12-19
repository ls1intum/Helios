package de.tum.cit.aet.helios.deployment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);
    Optional<Deployment> findFirstByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

}
