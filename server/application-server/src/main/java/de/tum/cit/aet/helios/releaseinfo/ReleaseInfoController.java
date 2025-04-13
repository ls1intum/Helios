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
import org.springframework.web.bind.annotation.PutMapping;
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
  public ResponseEntity<List<ReleaseInfoListDto>> getAllReleaseInfos() {
    return ResponseEntity.ok(releaseInfoService.getAllReleaseInfos());
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

  /**
   * Endpoint to generate release notes for a given tag.
   *
   * @param tagName Name of the tag
   * @return Generated release notes
   */
  @EnforceAtLeastMaintainer
  @PostMapping("/{tagName}/generate-release-notes")
  public ResponseEntity<String> generateReleaseNotes(@PathVariable String tagName) {
    return ResponseEntity.ok(releaseInfoService.generateReleaseNotes(tagName));
  }

  /**
   * Endpoint to update the release notes of a release candidate.
   *
   * @param name Name of the release candidate
   * @param releaseNotes DTO containing the updated release notes
   * @return Updated release info details
   */
  @EnforceAtLeastMaintainer
  @PutMapping("/{name}/release-notes")
  public ResponseEntity<Void> updateReleaseNotes(
      @PathVariable String name, @RequestBody ReleaseNotesDto releaseNotes) {
    releaseInfoService.updateReleaseNotes(name, releaseNotes.body());
    return ResponseEntity.ok().build();
  }

  /**
   * Endpoint to update the name of a release candidate.
   *
   * @param name Name of the release candidate to update
   * @param updateNameDto DTO containing the new name
   * @return Updated release info details
   */
  @EnforceAtLeastMaintainer
  @PutMapping("/{name}/update-name")
  public ResponseEntity<Void> updateReleaseName(
      @PathVariable String name, @RequestBody ReleaseCandidateNameUpdateDto updateNameDto) {
    releaseInfoService.updateReleaseName(name, updateNameDto.newName());
    return ResponseEntity.ok().build();
  }
}
