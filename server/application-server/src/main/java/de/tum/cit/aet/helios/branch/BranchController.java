package de.tum.cit.aet.helios.branch;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {

  private final BranchService branchService;

  @GetMapping
  public ResponseEntity<List<BranchInfoDto>> getAllBranches() {
    List<BranchInfoDto> branches = branchService.getAllBranches();
    return ResponseEntity.ok(branches);
  }

  @GetMapping("/repository/{repoId}/branch")
  public ResponseEntity<BranchDetailsDto> getBranchByRepositoryIdAndName(
      @PathVariable(name = "repoId") Long repoId, @RequestParam(name = "name") String name) {
    return branchService
        .getBranchByRepositoryIdAndName(repoId, name)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/repository/{repoId}/pin")
  public ResponseEntity<Void> setBranchPinnedByRepositoryIdAndNameAndUserId(
      @PathVariable(name = "repoId") Long repoId,
      @RequestParam(name = "name") String name,
      @RequestParam(name = "isPinned") Boolean isPinned) {
    branchService.setBranchPinnedByRepositoryIdAndName(repoId, name, isPinned);
    return ResponseEntity.ok().build();
  }
}
