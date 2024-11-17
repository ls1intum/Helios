package de.tum.cit.aet.helios.gitprovider.repository;

import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface RepositoryRepository
        extends JpaRepository<Repository, Long> {

    Repository findByNameWithOwner(String nameWithOwner);
}
