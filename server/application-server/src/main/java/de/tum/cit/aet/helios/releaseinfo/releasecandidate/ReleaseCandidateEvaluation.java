package de.tum.cit.aet.helios.releaseinfo.releasecandidate;

import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString(callSuper = true)
@Table(
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"release_candidate_id", "evaluated_by_id"})
    })
public class ReleaseCandidateEvaluation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  private ReleaseCandidate releaseCandidate;

  @Column(name = "is_working")
  private boolean isWorking;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluated_by_id", nullable = false)
  @ToString.Exclude
  private User evaluatedBy;

  @Column(length = 500)
  @Size(max = 500, message = "Comment cannot exceed 500 characters")
  private String comment;
}
