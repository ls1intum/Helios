package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
  List<TestResult> findByWorkflowRun(WorkflowRun workflowRun);
}
