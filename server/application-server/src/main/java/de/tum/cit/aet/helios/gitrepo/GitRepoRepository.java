package de.tum.cit.aet.helios.gitrepo;

import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface GitRepoRepository
        extends JpaRepository<GitRepository, Long> {

    GitRepository findByNameWithOwner(String nameWithOwner);
}
