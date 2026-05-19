package de.tum.cit.aet.helios.workflow.queue.web;

import de.tum.cit.aet.helios.workflow.queue.LabelSets;
import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.RunnerDto;
import de.tum.cit.aet.helios.workflow.queue.web.QueueDtos.RunnerPoolDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runners")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(name = "helios.queue.enabled", havingValue = "true")
public class RunnerController {

  private final RunnerRepository runnerRepository;

  @GetMapping
  public ResponseEntity<List<RunnerDto>> list() {
    Comparator<Runner> byName =
        Comparator.comparing(Runner::getName, Comparator.nullsLast(Comparator.naturalOrder()));
    List<RunnerDto> dtos = runnerRepository.findAll().stream()
        .sorted(byName)
        .map(this::toDto)
        .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{id}")
  public ResponseEntity<RunnerDto> byId(@PathVariable Long id) {
    return runnerRepository.findById(id)
        .map(this::toDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/pools")
  public ResponseEntity<List<RunnerPoolDto>> pools() {
    // Group by canonical-label hash so two runners with the same labels in different order
    // appear as one pool. Order may differ between webhook payloads and inventory polling.
    Map<List<String>, List<Runner>> byLabels = new HashMap<>();
    for (Runner r : runnerRepository.findAll()) {
      List<String> key = LabelSets.canonical(r.getLabels());
      byLabels.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
    }
    List<RunnerPoolDto> pools = new ArrayList<>();
    for (Map.Entry<List<String>, List<Runner>> e : byLabels.entrySet()) {
      int online = (int) e.getValue().stream()
          .filter(r -> r.getStatus() == Runner.Status.ONLINE).count();
      int busy = (int) e.getValue().stream()
          .filter(r -> r.getStatus() == Runner.Status.ONLINE && r.isBusy()).count();
      int idle = online - busy;
      int offline = e.getValue().size() - online;
      pools.add(new RunnerPoolDto(e.getKey(), online, busy, idle, offline));
    }
    return ResponseEntity.ok(pools);
  }

  private RunnerDto toDto(Runner r) {
    return new RunnerDto(
        r.getId(),
        r.getName(),
        r.getOs(),
        r.getStatus() == null ? null : r.getStatus().name(),
        r.isBusy(),
        r.getLabels(),
        r.getRunnerGroupId(),
        r.getRunnerGroupName(),
        r.getCurrentJobId(),
        r.getLastSeenAt(),
        r.getOfflineSince());
  }
}
