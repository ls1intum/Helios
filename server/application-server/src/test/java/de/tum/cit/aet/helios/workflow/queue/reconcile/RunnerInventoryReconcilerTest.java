package de.tum.cit.aet.helios.workflow.queue.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunnerInventoryReconcilerTest {

  @Mock GitHubRestClient restClient;
  @Mock RunnerRepository runnerRepository;
  @InjectMocks RunnerInventoryReconciler reconciler;

  private final ObjectMapper om = new ObjectMapper();

  @BeforeEach
  void setOrg() throws Exception {
    Field f = RunnerInventoryReconciler.class.getDeclaredField("githubOrg");
    f.setAccessible(true);
    f.set(reconciler, "ls1intum");
  }

  private ObjectNode pageWith(long id, String status, boolean busy) {
    ObjectNode root = om.createObjectNode();
    ArrayNode runners = root.putArray("runners");
    ObjectNode r = runners.addObject();
    r.put("id", id);
    r.put("name", "runner-" + id);
    r.put("os", "linux");
    r.put("status", status);
    r.put("busy", busy);
    ArrayNode labels = r.putArray("labels");
    labels.addObject().put("name", "self-hosted");
    labels.addObject().put("name", "linux");
    return root;
  }

  @Test
  void persistsOnlineRunnerFromInventoryResponse() {
    when(restClient.get(eq("/orgs/ls1intum/actions/runners?per_page=100&page=1")))
        .thenReturn(Optional.of(pageWith(101L, "online", false)));
    when(runnerRepository.findById(101L)).thenReturn(Optional.empty());

    reconciler.reconcile();

    ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
    verify(runnerRepository).save(captor.capture());
    Runner saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo(101L);
    assertThat(saved.getStatus()).isEqualTo(Runner.Status.ONLINE);
    assertThat(saved.isBusy()).isFalse();
    assertThat(saved.getLabels()).contains("self-hosted", "linux");
  }

  @Test
  void marksMissingRunnersOffline() {
    when(restClient.get(eq("/orgs/ls1intum/actions/runners?per_page=100&page=1")))
        .thenReturn(Optional.of(pageWith(101L, "online", false)));
    when(runnerRepository.findById(101L)).thenReturn(Optional.empty());

    reconciler.reconcile();

    verify(runnerRepository).markMissingOffline(anyList(), any());
  }

  @Test
  void earlyExitWhenRestReturnsEmpty() {
    when(restClient.get(any())).thenReturn(Optional.empty());

    reconciler.reconcile();

    verify(runnerRepository, times(0)).save(any());
    verify(runnerRepository, times(0)).markMissingOffline(any(), any());
  }
}
