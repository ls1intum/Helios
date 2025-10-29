package de.tum.cit.aet.helios.releaseinfo;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.CommitsSinceReleaseCandidateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateCreateDto;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseNameDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

  // Must be POST because the name is encoded in the body and a GET cannot have a body
  @PostMapping("/details")
  public ResponseEntity<ReleaseInfoDetailsDto> getReleaseInfoByName(
      @RequestBody ReleaseNameDto nameDto) {
    return ResponseEntity.ok(releaseInfoService.getReleaseInfoByName(nameDto.name()));
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

  @PostMapping("/evaluate")
  public ResponseEntity<Void> evaluate(@RequestBody ReleaseEvaluationDto evaluationDto) {
    releaseInfoService.evaluateReleaseCandidate(
        evaluationDto.name(), evaluationDto.isWorking(), evaluationDto.comment());
    return ResponseEntity.ok().build();
  }

  @EnforceAtLeastMaintainer
  @DeleteMapping
  public ResponseEntity<ReleaseInfoListDto> deleteReleaseCandidateByName(
      @RequestBody ReleaseNameDto nameDto) {
    return ResponseEntity.ok(releaseInfoService.deleteReleaseCandidateByName(nameDto.name()));
  }

  @EnforceAtLeastMaintainer
  @PostMapping("/publish")
  public ResponseEntity<Void> publishReleaseDraft(@RequestBody ReleaseNameDto nameDto) {
    releaseInfoService.publishReleaseDraft(nameDto.name());
    return ResponseEntity.ok().build();
  }

  /**
   * Endpoint to generate release notes for a given tag.
   *
   * @param releaseNameDto DTO containing the tag name
   * @return Generated release notes
   */
  @EnforceAtLeastMaintainer
  @PostMapping("/generate-release-notes")
  public ResponseEntity<String> generateReleaseNotes(@RequestBody ReleaseNameDto releaseNameDto) {
    return ResponseEntity.ok(releaseInfoService.generateReleaseNotes(releaseNameDto.name()));
  }

  /**
   * Endpoint to update the release notes of a release candidate.
   *
   * @param updateReleaseNotesDto DTO containing the name and updated release notes
   * @return Updated release info details
   */
  @EnforceAtLeastMaintainer
  @PutMapping("/release-notes")
  public ResponseEntity<Void> updateReleaseNotes(
      @RequestBody UpdateReleaseNotesDto updateReleaseNotesDto) {
    releaseInfoService.updateReleaseNotes(
        updateReleaseNotesDto.name(), updateReleaseNotesDto.notes());
    return ResponseEntity.ok().build();
  }

  /**
   * Endpoint to update the name of a release candidate.
   *
   * @param updateNameDto DTO containing the old name and the new name
   * @return Updated release info details
   */
  @EnforceAtLeastMaintainer
  @PutMapping("/update-name")
  public ResponseEntity<Void> updateReleaseName(
      @RequestBody ReleaseCandidateNameUpdateDto updateNameDto) {
    releaseInfoService.updateReleaseName(updateNameDto.oldName(), updateNameDto.newName());
    return ResponseEntity.ok().build();
  }
}
