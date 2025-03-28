package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion.DeploymentType;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record ReleaseCandidateDetailsDto(
    @NonNull String name,
    @NonNull CommitInfoDto commit,
    @NonNull BranchInfoDto branch,
    @NonNull List<ReleaseCandidateDeploymentDto> deployments,
    @NonNull List<ReleaseCandidateEvaluationDto> evaluations,
    @NonNull UserInfoDto createdBy,
    @NonNull OffsetDateTime createdAt) {

  public record ReleaseCandidateEvaluationDto(@NonNull UserInfoDto user, boolean isWorking) {
    public static ReleaseCandidateEvaluationDto fromEvaluation(
        @NonNull ReleaseCandidateEvaluation evaluation) {
      return new ReleaseCandidateEvaluationDto(
          UserInfoDto.fromUser(evaluation.getEvaluatedBy()), evaluation.isWorking());
    }
  }

  public static record ReleaseCandidateDeploymentDto(
      @NonNull Long id, @NonNull DeploymentType type, @NonNull Long environmentId) {
    public static ReleaseCandidateDeploymentDto fromDeployment(
        @NonNull LatestDeploymentUnion deployment) {

      return new ReleaseCandidateDeploymentDto(
          deployment.getId(), deployment.getType(), deployment.getEnvironment().getId());
    }
  }
}
