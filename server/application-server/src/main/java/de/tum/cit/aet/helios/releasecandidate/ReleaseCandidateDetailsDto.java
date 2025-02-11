package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.deployment.DeploymentDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public record ReleaseCandidateDetailsDto(
    @NonNull String name,
    @NonNull CommitInfoDto commit,
    BranchInfoDto branch,
    @NonNull List<DeploymentDto> deployments,
    @NonNull List<ReleaseCandidateEvaluationDto> evaluations,
    @NonNull UserInfoDto createdBy,
    @NonNull OffsetDateTime createdAt) {

  public record ReleaseCandidateEvaluationDto(
      @NonNull UserInfoDto user, @NonNull boolean isWorking) {
    public static ReleaseCandidateEvaluationDto fromEvaluation(
        @NonNull ReleaseCandidateEvaluation evaluation) {
      return new ReleaseCandidateEvaluationDto(
          UserInfoDto.fromUser(evaluation.getEvaluatedBy()), evaluation.isWorking());
    }
  }
}
