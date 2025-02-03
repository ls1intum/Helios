package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString(callSuper = true)
public class TagEvaluation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @JoinColumn(name = "is_working")
  private boolean isWorking;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluated_by_id")
  @ToString.Exclude
  private User evaluatedBy;
}
