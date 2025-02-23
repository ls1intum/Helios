package de.tum.cit.aet.helios.userpreference;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "user_preference", schema = "public")
@Getter
@Setter
@ToString(callSuper = true)
public class UserPreference {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @ManyToMany
  private Set<Branch> favouriteBranches;

  @ManyToMany
  private Set<PullRequest> favouritePullRequests;
}
