package de.tum.cit.aet.helios.environments.environments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EnvironmentRepository extends JpaRepository<EnvironmentInfo, Long> {

}