package de.tum.cit.aet.helios.environments.installed_applications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "installed_applicaitons", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class InstalledApplication {
    @Id
    @NotNull
    private Long id;

    @NotNull
    private String name;

    @NotNull
    private Long environmentId;
    
}
