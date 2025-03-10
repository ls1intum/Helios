package de.tum.cit.aet.helios.releaseinfo;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.CommitsSinceReleaseCandidateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateCreateDto;
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
@RequestMapping("/api/release-info")
@RequiredArgsConstructor
public class ReleaseInfoController {
  private final ReleaseInfoService releaseInfoService;

  @GetMapping
  public ResponseEntity<List<ReleaseInfoListDto>> getAllReleaseCandidates() {
    return ResponseEntity.ok(releaseInfoService.getAllReleaseCandidates());
  }

  @GetMapping("/{name}")
  public ResponseEntity<ReleaseInfoDetailsDto> getReleaseInfoByName(@PathVariable String name) {
    return ResponseEntity.ok(releaseInfoService.getReleaseInfoByName(name));
  }

  @GetMapping("/newcommits")
  public ResponseEntity<CommitsSinceReleaseCandidateDto> getCommitsSinceLastReleaseCandidate(
      @RequestParam String branch) {
    return ResponseEntity.ok(
        releaseInfoService.getCommitsFromBranchSinceLastReleaseCandidate(branch));
  }

  @PostMapping
  @EnforceAtLeastMaintainer
  public ResponseEntity<ReleaseInfoListDto> createReleaseCandidate(
      @RequestBody ReleaseCandidateCreateDto releaseCandidate) {
    return ResponseEntity.ok(releaseInfoService.createReleaseCandidate(releaseCandidate));
  }

  @PostMapping("{name}/evaluate/{isWorking}")
  public ResponseEntity<Void> evaluate(@PathVariable String name, @PathVariable boolean isWorking) {
    releaseInfoService.evaluateReleaseCandidate(name, isWorking);
    return ResponseEntity.ok().build();
  }

  @EnforceAtLeastMaintainer
  @DeleteMapping("/{name}")
  public ResponseEntity<ReleaseInfoListDto> deleteReleaseCandidateByName(
      @PathVariable String name) {
    return ResponseEntity.ok(releaseInfoService.deleteReleaseCandidateByName(name));
  }

  @EnforceAtLeastMaintainer
  @PostMapping("/{name}/publish")
  public ResponseEntity<Void> publishReleaseDraft(@PathVariable String name) {
    releaseInfoService.publishReleaseDraft(name);
    return ResponseEntity.ok().build();
  }
}
