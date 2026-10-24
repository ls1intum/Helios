package de.tum.cit.aet.helios.auth.github.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Persisted GitHub token material for one GitHub user, so Helios can hold a valid user access
 * token for deployment approvals without an interactive session. The token columns hold AES-GCM
 * ciphertext (see {@link TokenCipher}); this entity never carries plaintext, and its
 * {@code toString()} excludes them.
 */
@Entity
@Table(name = "github_user_token")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"accessTokenEnc", "refreshTokenEnc"})
public class GitHubUserToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "github_login", nullable = false, unique = true)
  private String githubLogin;

  @Column(name = "access_token_enc")
  private String accessTokenEnc;

  @Column(name = "refresh_token_enc")
  private String refreshTokenEnc;

  @Column(name = "access_token_expires_at")
  private OffsetDateTime accessTokenExpiresAt;

  @Column(name = "refresh_token_expires_at")
  private OffsetDateTime refreshTokenExpiresAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void stampUpdatedAt() {
    this.updatedAt = OffsetDateTime.now();
  }
}
