package de.tum.cit.aet.helios.environment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
}
