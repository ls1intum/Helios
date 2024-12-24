package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "workflow")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
// https://docs.github.com/en/rest/actions/workflows?apiVersion=2022-11-28#get-a-workflow
public class Workflow extends BaseGitServiceEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id")
  private GitRepository repository;

  private String name;

  private String path;

  // Custom field
  private String fileNameWithExtension;

  @NonNull
  @Enumerated(EnumType.STRING)
  private State state;

  private String url;

  private String htmlUrl;

  private String badgeUrl;

  // Custom field for Repository Settings
  @NonNull
  @Enumerated(EnumType.STRING)
  private Label label = Label.NONE;

  public enum State {
    ACTIVE,
    DELETED,
    DISABLED_FORK,
    DISABLED_INACTIVITY,
    DISABLED_MANUALLY,
    UNKNOWN,
  }

  public enum Label {
    BUILD,
    DEPLOYMENT,
    NONE
  }
}
