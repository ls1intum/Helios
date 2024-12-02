package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import de.tum.cit.aet.helios.issue.Issue.State;
import de.tum.cit.aet.helios.user.UserInfoDTO;

import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchInfoDTO(
        @NonNull String name,
        RepositoryInfoDTO repository
        ) {

    public static BranchInfoDTO fromBranch(Branch branch) {
        return new BranchInfoDTO(
                branch.getName(),
                RepositoryInfoDTO.fromRepository(branch.getRepository())
                );
    }

}