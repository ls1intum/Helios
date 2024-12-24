package de.tum.cit.aet.helios.branch;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

  private final BranchService branchService;

  public BranchController(BranchService branchService) {
    this.branchService = branchService;
  }

  @GetMapping
  public ResponseEntity<List<BranchInfoDTO>> getAllBranches() {
    List<BranchInfoDTO> branches = branchService.getAllBranches();
    return ResponseEntity.ok(branches);
  }

  @GetMapping("/repository/{repoId}/name/{name}")
  public ResponseEntity<BranchInfoDTO> getBranchByRepositoryIdAndName(
      @PathVariable Long repoId, @PathVariable String name) {
    return branchService
        .getBranchInfo(repoId, name)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
