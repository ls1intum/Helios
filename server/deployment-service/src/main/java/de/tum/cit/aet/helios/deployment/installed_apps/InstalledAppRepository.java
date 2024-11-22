package de.tum.cit.aet.helios.deployment.installed_apps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstalledAppRepository extends JpaRepository<InstalledApp, Long> {
    
}
