package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@IdClass(ReleaseCandidateEvaluationId.class)
@ToString(callSuper = true)
public class ReleaseCandidateEvaluation {
  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  private ReleaseCandidate releaseCandidate;

  @Column(name = "is_working")
  private boolean isWorking;

  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluated_by_id")
  @ToString.Exclude
  private User evaluatedBy;
}
