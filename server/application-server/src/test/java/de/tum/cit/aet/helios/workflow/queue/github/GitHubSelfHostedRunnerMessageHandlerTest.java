package de.tum.cit.aet.helios.workflow.queue.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.github.GitHubSelfHostedRunnerPayload.RunnerGroup;
import de.tum.cit.aet.helios.workflow.queue.github.GitHubSelfHostedRunnerPayload.RunnerLabel;
import de.tum.cit.aet.helios.workflow.queue.github.GitHubSelfHostedRunnerPayload.SelfHostedRunner;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubSelfHostedRunnerMessageHandlerTest {

  @Mock RunnerRepository runnerRepository;
  @InjectMocks GitHubSelfHostedRunnerMessageHandler handler;

  private void invoke(GitHubSelfHostedRunnerPayload payload) throws Exception {
    Method m = GitHubSelfHostedRunnerMessageHandler.class.getDeclaredMethod(
        "handleMessage", GitHubSelfHostedRunnerPayload.class);
    m.setAccessible(true);
    m.invoke(handler, payload);
  }

  private GitHubSelfHostedRunnerPayload payload(String action, String runnerStatus) {
    SelfHostedRunner r = new SelfHostedRunner(
        101L, "runner-1", "linux", runnerStatus, false,
        List.of(new RunnerLabel(1L, "self-hosted", "read-only"),
            new RunnerLabel(2L, "linux", "read-only")),
        new RunnerGroup(5L, "default"));
    return new GitHubSelfHostedRunnerPayload(action, r, null);
  }

  @Test
  void onlineActionMarksRunnerOnline() throws Exception {
    when(runnerRepository.findById(101L)).thenReturn(Optional.empty());
    invoke(payload("online", "online"));

    ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
    verify(runnerRepository).save(captor.capture());
    Runner saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo(101L);
    assertThat(saved.getStatus()).isEqualTo(Runner.Status.ONLINE);
    assertThat(saved.getOfflineSince()).isNull();
    assertThat(saved.getLabels()).contains("self-hosted", "linux");
    assertThat(saved.getRunnerGroupName()).isEqualTo("default");
  }

  @Test
  void offlineActionMarksRunnerOfflineAndStampsOfflineSince() throws Exception {
    when(runnerRepository.findById(101L)).thenReturn(Optional.empty());
    invoke(payload("offline", "offline"));

    ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
    verify(runnerRepository).save(captor.capture());
    Runner saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(Runner.Status.OFFLINE);
    assertThat(saved.getOfflineSince()).isNotNull();
  }

  @Test
  void nullPayloadIsSafe() throws Exception {
    invoke(new GitHubSelfHostedRunnerPayload("offline", null, null));
    verify(runnerRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
  }
}
