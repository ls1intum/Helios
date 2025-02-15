package de.tum.cit.aet.helios.tests;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
  List<TestSuite> findByWorkflowRunId(long workflowRunId);
}
