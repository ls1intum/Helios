package de.tum.cit.aet.helios.deployment.deployments;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Collection<Deployment> findAllByBranchName(String branchName);

}