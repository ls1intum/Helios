package de.tum.cit.aet.helios.workflow.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the canonical pipeline (always-visible Build/Tests/Quality nodes) for a branch or pull
 * request. Tenant-scoped via the {@code X-REPOSITORY-ID} header (resolved inside
 * {@link PipelineService} through the run lookups).
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

  private final PipelineService pipelineService;

  @GetMapping("/branch")
  public ResponseEntity<PipelineDto> getPipelineByBranch(@RequestParam String branch) {
    return ResponseEntity.ok(pipelineService.getPipelineForBranch(branch));
  }

  @GetMapping("/pr/{pullRequestId}")
  public ResponseEntity<PipelineDto> getPipelineByPullRequest(@PathVariable Long pullRequestId) {
    return ResponseEntity.ok(pipelineService.getPipelineForPullRequest(pullRequestId));
  }
}
