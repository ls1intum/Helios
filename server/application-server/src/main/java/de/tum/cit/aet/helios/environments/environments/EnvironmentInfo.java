package de.tum.cit.aet.helios.environments.environments;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.cit.aet.helios.environments.installed_applications.InstalledApplication;
import de.tum.cit.aet.helios.gitprovider.user.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "environments")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class EnvironmentInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    private String url;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private User createdBy;

    private OffsetDateTime createdAt;

    private Status status;
    
    public enum Status {
        UP, DOWN
    }
    
    @ManyToMany
    @JoinTable(name = "environment_installed_applications", joinColumns = @JoinColumn(name = "environment_id"), inverseJoinColumns = @JoinColumn(name = "id"))
    @ToString.Exclude
    private Set<InstalledApplication> installedApplications = new HashSet<>();

    // who is using the environment currently USER
    // deployment info
    // deployed commit info
}
