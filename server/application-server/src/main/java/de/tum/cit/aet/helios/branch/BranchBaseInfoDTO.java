package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import de.tum.cit.aet.helios.issue.Issue.State;

import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchBaseInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull Boolean isProtected,
        RepositoryInfoDTO repository) {

    public static BranchBaseInfoDTO fromBranch(Branch branch) {
        return new BranchBaseInfoDTO(
                branch.getId(),
                branch.getName(),
                branch.isProtected(),
                RepositoryInfoDTO.fromRepository(branch.getRepository()));
    }
}