package de.tum.cit.aet.helios.branch;

import java.util.List;
import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.helios.gitrepo.GitRepository;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByNameAndRepositoryId(String name, Long repositoryId);
    void deleteByNameAndRepositoryId(String name, Long repositoryId);
    List<Branch> findByRepositoryId(Long repositoryId);
}