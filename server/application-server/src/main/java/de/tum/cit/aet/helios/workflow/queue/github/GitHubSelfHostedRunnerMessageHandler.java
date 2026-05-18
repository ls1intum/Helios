package de.tum.cit.aet.helios.workflow.queue.github;

import de.tum.cit.aet.helios.nats.JacksonMessageHandler;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/** Handles org-level {@code self_hosted_runner} events. See plan §B3. */
@Component
@Log4j2
@RequiredArgsConstructor
public class GitHubSelfHostedRunnerMessageHandler
    extends JacksonMessageHandler<GitHubSelfHostedRunnerPayload> {

  private final RunnerRepository runnerRepository;

  @Override
  protected Class<GitHubSelfHostedRunnerPayload> getPayloadClass() {
    return GitHubSelfHostedRunnerPayload.class;
  }

  @Override
  public String getSubjectPattern() {
    return "github.*.*.self_hosted_runner";
  }

  @Override
  @Transactional
  protected void handleMessage(GitHubSelfHostedRunnerPayload payload) {
    if (payload == null || payload.selfHostedRunner() == null) {
      return;
    }
    GitHubSelfHostedRunnerPayload.SelfHostedRunner src = payload.selfHostedRunner();
    if (src.id() == null) {
      return;
    }

    Runner runner = runnerRepository.findById(src.id()).orElseGet(Runner::new);
    boolean isNew = runner.getId() == null;
    runner.setId(src.id());
    if (src.name() != null) {
      runner.setName(src.name());
    }
    if (src.os() != null) {
      runner.setOs(src.os());
    }
    if (src.runnerGroup() != null) {
      runner.setRunnerGroupId(src.runnerGroup().id());
      runner.setRunnerGroupName(src.runnerGroup().name());
    }
    runner.setLabels(extractLabelNames(src.labels()));
    if (src.busy() != null) {
      runner.setBusy(src.busy());
    }

    OffsetDateTime now = OffsetDateTime.now();
    if (isNew) {
      runner.setFirstRegisteredAt(now);
    }
    runner.setLastSeenAt(now);

    String action = payload.action() == null ? "" : payload.action().toLowerCase();
    switch (action) {
      case "online", "created" -> {
        runner.setStatus(Runner.Status.ONLINE);
        runner.setOfflineSince(null);
      }
      case "offline", "removed" -> {
        runner.setStatus(Runner.Status.OFFLINE);
        if (runner.getOfflineSince() == null) {
          runner.setOfflineSince(now);
        }
      }
      default -> {
        // For other actions, sync from the payload's status field if present.
        if ("online".equalsIgnoreCase(src.status())) {
          runner.setStatus(Runner.Status.ONLINE);
          runner.setOfflineSince(null);
        } else if ("offline".equalsIgnoreCase(src.status())) {
          runner.setStatus(Runner.Status.OFFLINE);
          if (runner.getOfflineSince() == null) {
            runner.setOfflineSince(now);
          }
        }
      }
    }
    runnerRepository.save(runner);
    log.debug("Persisted runner id={} status={} action={}", runner.getId(), runner.getStatus(),
        action);
  }

  private List<String> extractLabelNames(List<GitHubSelfHostedRunnerPayload.RunnerLabel> labels) {
    List<String> names = new ArrayList<>();
    if (labels == null) {
      return names;
    }
    for (GitHubSelfHostedRunnerPayload.RunnerLabel label : labels) {
      if (label != null && label.name() != null) {
        names.add(label.name());
      }
    }
    return names;
  }
}
