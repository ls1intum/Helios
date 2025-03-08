package de.tum.cit.aet.helios.commit;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/commits")
public class CommitController {
  private final CommitService commitService;

  // TODO: Fix the return type to notFound if the commit is not found
  @GetMapping("/repository/{repoId}/commit/{sha}")
  public ResponseEntity<CommitInfoDto> getCommitByRepositoryIdAndName(
      @PathVariable Long repoId, @PathVariable String sha) {
    return commitService
        .getCommitByShaAndRepositoryId(sha, repoId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.ok(null));
  }
}
