package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.deployment.DeploymentDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record TagDetailsDto(
    @NonNull String name,
    @NonNull CommitInfoDto commit,
    BranchInfoDto branch,
    @NonNull List<DeploymentDto> deployments,
    @NonNull List<UserInfoDto> markedWorkingBy,
    @NonNull List<UserInfoDto> markedBrokenBy,
    @NonNull UserInfoDto createdBy,
    @NonNull OffsetDateTime createdAt) {}
