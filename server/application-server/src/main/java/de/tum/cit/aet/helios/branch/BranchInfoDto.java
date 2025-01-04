package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchInfoDto(
    @NonNull String name,
    @NonNull String commitSha,
    int aheadBy,
    int behindBy,
    boolean isDefault,
    boolean isProtected,
    OffsetDateTime updatedAt,
    UserInfoDto updatedBy,
    RepositoryInfoDto repository) {

  public static BranchInfoDto fromBranch(Branch branch) {
    return new BranchInfoDto(
        branch.getName(),
        branch.getCommitSha(),
        branch.getAheadBy(),
        branch.getBehindBy(),
        branch.isDefault(),
        branch.isProtection(),
        branch.getUpdatedAt(),
        UserInfoDto.fromUser(branch.getUpdatedBy()),
        RepositoryInfoDto.fromRepository(branch.getRepository()));
  }
}
