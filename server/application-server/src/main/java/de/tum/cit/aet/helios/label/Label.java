package de.tum.cit.aet.helios.label;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.issue.Issue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "label")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "gitRepositoryFilter")
public class Label {

  @Id
  protected Long id;

  @NonNull
  private String name;

  private String description;

  // 6-character hex code, without the leading #, identifying the color
  @NonNull
  private String color;

  @ManyToMany(mappedBy = "labels")
  @ToString.Exclude
  private Set<Issue> issues = new HashSet<>();

  @ManyToOne
  @JoinColumn(name = "repository_id")
  @ToString.Exclude
  private GitRepository repository;

//  @ManyToMany(mappedBy = "labels")
//  @ToString.Exclude
//  private Set<Team> teams = new HashSet<>();
//
//  public void removeAllTeams() {
//    this.teams.forEach(team -> team.getLabels().remove(this));
//    this.teams.clear();
//  }
  // Ignored GitHub properties:
  // - default
}