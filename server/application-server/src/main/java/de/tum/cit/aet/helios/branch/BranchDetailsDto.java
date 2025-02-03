package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchDetailsDto(
    @NonNull String name,
    @NonNull String commitSha,
    int aheadBy,
    int behindBy,
    boolean isDefault,
    boolean isProtected,
    String tagName,
    OffsetDateTime updatedAt,
    UserInfoDto updatedBy,
    RepositoryInfoDto repository) {

  public static BranchDetailsDto fromBranch(Branch branch, String tagName) {
    return new BranchDetailsDto(
        branch.getName(),
        branch.getCommitSha(),
        branch.getAheadBy(),
        branch.getBehindBy(),
        branch.isDefault(),
        branch.isProtection(),
        tagName,
        branch.getUpdatedAt(),
        UserInfoDto.fromUser(branch.getUpdatedBy()),
        RepositoryInfoDto.fromRepository(branch.getRepository()));
  }
}
