package de.tum.cit.aet.helios.workflow.queue.reconcile;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.cit.aet.helios.github.GitHubRestClient;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Polls {@code /orgs/{org}/actions/runners} every 60s. See plan §B5. */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class RunnerInventoryReconciler {

  private final GitHubRestClient restClient;
  private final RunnerRepository runnerRepository;

  @Value("${helios.github.org:ls1intum}")
  private String githubOrg;

  @Scheduled(fixedRateString = "${helios.queue.reconcile.runner.fixedRateMs:60000}")
  @Transactional
  public void reconcile() {
    List<Long> seen = new ArrayList<>();
    int page = 1;
    int perPage = 100;
    while (true) {
      String path =
          "/orgs/" + githubOrg + "/actions/runners?per_page=" + perPage + "&page=" + page;
      Optional<JsonNode> body = restClient.get(path);
      if (body.isEmpty()) {
        log.debug("RunnerInventoryReconciler: no body (304 or error) for page {}", page);
        break;
      }
      JsonNode runners = body.get().get("runners");
      if (runners == null || !runners.isArray() || runners.isEmpty()) {
        break;
      }
      OffsetDateTime now = OffsetDateTime.now();
      for (JsonNode node : runners) {
        Long id = node.path("id").isMissingNode() ? null : node.get("id").asLong();
        if (id == null) {
          continue;
        }
        seen.add(id);
        Runner runner = runnerRepository.findById(id).orElseGet(Runner::new);
        boolean isNew = runner.getId() == null;
        runner.setId(id);
        if (node.hasNonNull("name")) {
          runner.setName(node.get("name").asText());
        }
        if (node.hasNonNull("os")) {
          runner.setOs(node.get("os").asText());
        }
        runner.setBusy(node.path("busy").asBoolean(false));
        String status = node.path("status").asText("offline");
        if ("online".equalsIgnoreCase(status)) {
          runner.setStatus(Runner.Status.ONLINE);
          runner.setOfflineSince(null);
        } else {
          runner.setStatus(Runner.Status.OFFLINE);
          if (runner.getOfflineSince() == null) {
            runner.setOfflineSince(now);
          }
        }
        JsonNode labels = node.get("labels");
        List<String> labelNames = new ArrayList<>();
        if (labels != null && labels.isArray()) {
          for (JsonNode l : labels) {
            if (l.hasNonNull("name")) {
              labelNames.add(l.get("name").asText());
            }
          }
        }
        runner.setLabels(labelNames);
        if (isNew) {
          runner.setFirstRegisteredAt(now);
        }
        runner.setLastSeenAt(now);
        runnerRepository.save(runner);
      }
      if (runners.size() < perPage) {
        break;
      }
      page++;
    }
    if (!seen.isEmpty()) {
      int markedOffline = runnerRepository.markMissingOffline(seen, OffsetDateTime.now());
      if (markedOffline > 0) {
        log.info("RunnerInventoryReconciler: marked {} runners OFFLINE", markedOffline);
      }
    }
  }
}
