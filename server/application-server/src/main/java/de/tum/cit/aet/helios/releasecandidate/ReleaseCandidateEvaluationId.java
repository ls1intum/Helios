package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.user.User;
import java.io.Serializable;
import java.util.Objects;

public class ReleaseCandidateEvaluationId implements Serializable {

  private ReleaseCandidate releaseCandidate;
  private User evaluatedBy;

  public ReleaseCandidateEvaluationId() {}

  public ReleaseCandidateEvaluationId(ReleaseCandidate releaseCandidate, User evaluatedBy) {
    this.releaseCandidate = releaseCandidate;
    this.evaluatedBy = evaluatedBy;
  }

  // Equals and hashCode
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReleaseCandidateEvaluationId releaseCandidateEvalId = (ReleaseCandidateEvaluationId) o;
    return Objects.equals(releaseCandidate, releaseCandidateEvalId.releaseCandidate)
        && Objects.equals(evaluatedBy, releaseCandidateEvalId.evaluatedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(releaseCandidate, evaluatedBy);
  }
}
