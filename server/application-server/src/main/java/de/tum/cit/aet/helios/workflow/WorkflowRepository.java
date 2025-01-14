package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow.Label;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
  List<Workflow> findByRepository(GitRepository repository);

  List<Workflow> findByRepositoryRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

  List<Workflow> findByStateOrderByCreatedAtDesc(Workflow.State state);

  Workflow findFirstByLabelOrderByCreatedAtDesc(Label deployment);
}
