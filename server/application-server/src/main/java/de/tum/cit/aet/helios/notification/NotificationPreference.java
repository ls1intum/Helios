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

  public enum Type {
    DEPLOYMENT_FAILED,
  }

  public NotificationPreference(User user, Type type, boolean enabled) {
    this.user = user;
    this.type = type;
    this.enabled = enabled;
  }
}
