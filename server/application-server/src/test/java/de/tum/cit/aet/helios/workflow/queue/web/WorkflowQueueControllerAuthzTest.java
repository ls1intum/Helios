package de.tum.cit.aet.helios.workflow.queue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertEventRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRule;
import de.tum.cit.aet.helios.workflow.queue.QueueAlertRuleRepository;
import de.tum.cit.aet.helios.workflow.queue.QueueEtaService;
import de.tum.cit.aet.helios.workflow.queue.QueueWaitStatRepository;
import de.tum.cit.aet.helios.workflow.queue.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.queue.reconcile.WorkflowJobBackfillService;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.AlertRuleDto;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guards against the cross-repo write IDOR: {@code @EnforceAtLeastWritePermission} grants the WRITE
 * role for the repo in the X-Repository-Id header (the request's {@link RepositoryContext}), while
 * the alert-rule write endpoints address the repo via the {@code repoId} path variable. The
 * controller must reject any request whose path repo differs from the authorized (context) repo.
 */
class WorkflowQueueControllerAuthzTest {

  private static final long CONTEXT_REPO = 7L;
  private static final long OTHER_REPO = 999L;

  private QueueAlertRuleRepository ruleRepository;
  private WorkflowQueueController controller;

  private static AlertRuleDto validDto() {
    return new AlertRuleDto(
        null, "QUEUE_P95_OVER", 600, 5, null, null, List.of("EMAIL"), true, "");
  }

  @BeforeEach
  void setUp() {
    ruleRepository = mock(QueueAlertRuleRepository.class);
    controller = new WorkflowQueueController(
        mock(WorkflowJobRepository.class),
        mock(QueueWaitStatRepository.class),
        ruleRepository,
        mock(QueueAlertEventRepository.class),
        mock(QueueEtaService.class),
        mock(WorkflowJobBackfillService.class));
  }

  @AfterEach
  void tearDown() {
    RepositoryContext.clear();
  }

  @Test
  void createRuleRejectsWhenPathRepoDiffersFromContext() {
    RepositoryContext.setRepositoryId(String.valueOf(CONTEXT_REPO));

    assertThatThrownBy(() -> controller.createRule(OTHER_REPO, validDto()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(ruleRepository, never()).save(any());
  }

  @Test
  void createRuleRejectsWhenNoRepoInContext() {
    // No RepositoryContext set (e.g. header missing) must not fall through to a write.
    assertThatThrownBy(() -> controller.createRule(CONTEXT_REPO, validDto()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(ruleRepository, never()).save(any());
  }

  @Test
  void updateRuleRejectsWhenPathRepoDiffersFromContext() {
    RepositoryContext.setRepositoryId(String.valueOf(CONTEXT_REPO));

    assertThatThrownBy(() -> controller.updateRule(OTHER_REPO, 1L, validDto()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(ruleRepository, never()).findByIdAndRepositoryId(any(), any());
  }

  @Test
  void deleteRuleRejectsWhenPathRepoDiffersFromContext() {
    RepositoryContext.setRepositoryId(String.valueOf(CONTEXT_REPO));

    assertThatThrownBy(() -> controller.deleteRule(OTHER_REPO, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(ruleRepository, never()).deleteByIdAndRepositoryId(any(), any());
  }

  @Test
  void createRuleSucceedsAndScopesToContextRepoWhenPathMatches() {
    RepositoryContext.setRepositoryId(String.valueOf(CONTEXT_REPO));
    when(ruleRepository.save(any(QueueAlertRule.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = controller.createRule(CONTEXT_REPO, validDto());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().repositoryId()).isEqualTo(CONTEXT_REPO);
    verify(ruleRepository).save(any(QueueAlertRule.class));
  }
}
