package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One row per (deployment, reviewer) tracking the lifecycle of an approval decision.
 *
 * <p>Three sources of decisions converge here, distinguished by {@link Via}:
 *
 * <ul>
 *   <li>{@code AUTO} — Helios auto-approved on behalf of the deployer because they're a required
 *       reviewer (Phase 1). No token is issued.
 *   <li>{@code IN_APP} — A reviewer clicked Approve/Decline inside Helios (Phase 2).
 *   <li>{@code EMAIL_LINK} — A reviewer responded via a one-click link in a Helios-issued email
 *       (Phase 3, not yet wired). {@link #tokenHash} carries the SHA-256 of the link's token.
 * </ul>
 *
 * <p>The {@link State} machine starts in {@code PENDING} (for any IN_APP/EMAIL_LINK request that
 * was created proactively). For AUTO rows the row is created already in a terminal state
 * ({@code APPROVED} or {@code FAILED_AT_GITHUB}). Once any sibling row for the same deployment
 * resolves, the remaining outstanding rows are flipped to {@code CONSUMED_BY_OTHER} in the same
 * transaction so the UI can show "already handled".
 */
@Entity
@Table(name = "deployment_approval_request")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"heliosDeployment", "reviewer"})
public class DeploymentApprovalRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "helios_deployment_id", nullable = false)
  private HeliosDeployment heliosDeployment;

  /**
   * Helios {@link User} row for the reviewer. Nullable because a required GitHub reviewer might
   * not (yet) have a corresponding Helios account; in that case {@link #reviewerLogin} still
   * carries the GitHub login so the row is meaningful for audit.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewer_id")
  private User reviewer;

  @Column(name = "reviewer_login", nullable = false, length = 255)
  private String reviewerLogin;

  /**
   * SHA-256 hex of the email-link token. Plaintext lives only in the URL. Null for {@code AUTO}
   * rows where no token is ever issued.
   */
  @Column(name = "token_hash", length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private State state;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Via via;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "email_sent_at")
  private OffsetDateTime emailSentAt;

  @Column(name = "responded_at")
  private OffsetDateTime respondedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = OffsetDateTime.now();
    }
  }

  public enum State {
    /** A reviewer can still act on this. */
    PENDING,
    /** Reviewer approved (or auto-approval succeeded). Terminal. */
    APPROVED,
    /** Reviewer declined (GitHub workflow rejected). Terminal. */
    DECLINED,
    /** Token TTL elapsed without a response. Terminal. */
    EXPIRED,
    /** Another reviewer resolved the deployment first. Terminal. */
    CONSUMED_BY_OTHER,
    /**
     * The user clicked Approve/Decline but the GitHub call failed (token exchange, 5xx, reviewer
     * lost access). Distinct from a missed click — used to surface a clean retry path.
     */
    FAILED_AT_GITHUB
  }

  public enum Via {
    /** Helios auto-approved on behalf of a deployer who is a required reviewer. */
    AUTO,
    /** A reviewer used the Helios UI directly. */
    IN_APP,
    /** A reviewer clicked the link in a Helios-issued email. */
    EMAIL_LINK
  }
}
