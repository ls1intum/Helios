package de.tum.cit.aet.helios.deployment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
}
