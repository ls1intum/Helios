package de.tum.cit.aet.helios.deployment.deployments;


import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Deployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String branchName;

    private String commitHash;

    private String pullRequest;

    private Long deployedByUserId;

    private OffsetDateTime deployedAt;
    

    public Deployment(DeploymentDTO deploymentDto) {
        this.branchName = deploymentDto.branchName();
        this.commitHash = deploymentDto.commitHash();
        this.pullRequest = deploymentDto.pullRequest();
        this.deployedByUserId = deploymentDto.deployedByUserId();
        this.deployedAt = OffsetDateTime.now();
    }
}
