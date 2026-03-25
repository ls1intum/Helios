package de.tum.cit.aet.helios.releaseinfo.release;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "release",
    schema = "public",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"tag_name", "repository_id"})})
@Getter
@Setter
@ToString(callSuper = true)
public class Release extends BaseGitServiceEntity {
  @Column(name = "github_url")
  private String githubUrl;

  @Column(name = "name")
  private String name;

  @Column(name = "tag_name")
  private String tagName;

  @Column(columnDefinition = "TEXT")
  private String body;

  @Column(name = "is_draft")
  private boolean isDraft;

  @Column(name = "is_prerelease")
  private boolean isPrerelease;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creator_id")
  private User creator;
}
