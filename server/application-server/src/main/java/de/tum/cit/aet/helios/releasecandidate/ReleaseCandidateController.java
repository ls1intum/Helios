package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/release-candidate")
@RequiredArgsConstructor
public class ReleaseCandidateController {
  private final ReleaseCandidateService releaseCandidateService;

  @GetMapping
  public ResponseEntity<List<ReleaseCandidateInfoDto>> getAllReleaseCandidates() {
    return ResponseEntity.ok(releaseCandidateService.getAllReleaseCandidates());
  }

  @GetMapping("/{name}")
  public ResponseEntity<ReleaseCandidateDetailsDto> getReleaseCandidateByName(
      @PathVariable String name) {
    return ResponseEntity.ok(releaseCandidateService.getReleaseCandidateByName(name));
  }

  @GetMapping("/newcommits")
  public ResponseEntity<CommitsSinceReleaseCandidateDto> getCommitsSinceLastReleaseCandidate(
      @RequestParam String branch) {
    return ResponseEntity.ok(
        releaseCandidateService.getCommitsFromBranchSinceLastReleaseCandidate(branch));
  }

  @PostMapping
  @EnforceAtLeastMaintainer
  public ResponseEntity<ReleaseCandidateInfoDto> createReleaseCandidate(
      @RequestBody ReleaseCandidateCreateDto releaseCandidate) {
    return ResponseEntity.ok(releaseCandidateService.createReleaseCandidate(releaseCandidate));
  }

  @PostMapping("{name}/evaluate/{isWorking}")
  public ResponseEntity<Void> evaluate(@PathVariable String name, @PathVariable boolean isWorking) {
    releaseCandidateService.evaluateReleaseCandidate(name, isWorking);
    return ResponseEntity.ok().build();
  }

  @EnforceAtLeastMaintainer
  @DeleteMapping("/{name}")
  public ResponseEntity<ReleaseCandidateInfoDto> deleteReleaseCandidateByName(
      @PathVariable String name) {
    return ResponseEntity.ok(releaseCandidateService.deleteReleaseCandidateByName(name));
  }
}
