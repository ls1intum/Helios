package de.tum.cit.aet.helios.notification;

import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a user's notification preference for different types of notifications.
 * Each user can have multiple notification preferences, each associated with a specific type of
 * notification.
 */
@Entity
@Table(name = "notification_preference", schema = "public", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class NotificationPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Type type;

  @Column(nullable = false)
  private boolean enabled;

  /**
   * Enum representing the different types of notifications.
   *
   * <p>The DB-side {@code chk_notification_type} CHECK constraint on the {@code type} column
   * must be updated in lockstep when values are added or removed — see Flyway migrations.
   */
  public enum Type {
    DEPLOYMENT_FAILED,
    LOCK_EXPIRED,
    LOCK_UNLOCKED,
    /**
     * Sent to each User-type required reviewer when a deployment to a protected environment is
     * deferred to manual review (i.e. when the deployment's
     * {@code auto_approval_decision} is stamped {@code DEFERRED_TO_REVIEWERS}). The email links
     * back to the Helios pending-approvals page where the reviewer can approve / decline; auth
     * is via the regular Keycloak login + reviewer-list check (no extra token in the URL).
     */
    DEPLOYMENT_APPROVAL_REQUEST,
  }

  /**
   * Constructor for creating a new NotificationPreference.
   *
   * @param user the user associated with this notification preference
   * @param type the type of notification
   * @param enabled whether the notification is enabled or not
   */
  public NotificationPreference(User user, Type type, boolean enabled) {
    this.user = user;
    this.type = type;
    this.enabled = enabled;
  }
}
