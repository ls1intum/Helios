package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchInfoDto(
    @NonNull String name, @NonNull String commitSha, RepositoryInfoDto repository) {

  public static BranchInfoDto fromBranch(Branch branch) {
    return new BranchInfoDto(
        branch.getName(),
        branch.getCommitSha(),
        RepositoryInfoDto.fromRepository(branch.getRepository()));
  }
}
