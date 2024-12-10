package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    List<Workflow> findByRepository(GitRepository repository);

    List<Workflow> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<Workflow> findByStateOrderByCreatedAtDesc(Workflow.State state);

}
