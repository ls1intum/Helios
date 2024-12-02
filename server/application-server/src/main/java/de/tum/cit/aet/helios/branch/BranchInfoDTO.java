package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;

import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchInfoDTO(
        @NonNull String name,
        @NonNull String commit_sha,
        RepositoryInfoDTO repository
        ) {

    public static BranchInfoDTO fromBranch(Branch branch) {
        return new BranchInfoDTO(
                branch.getName(),
                branch.getCommit_sha(),
                RepositoryInfoDTO.fromRepository(branch.getRepository())
        );
    }

}
