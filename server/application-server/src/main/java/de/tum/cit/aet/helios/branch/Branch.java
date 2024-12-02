package de.tum.cit.aet.helios.branch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;

@Entity
@IdClass(BranchId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Branch {

    @Id
    private String name;

    @Id
    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    @ToString.Exclude
    private GitRepository repository;

    private String commit_sha;

    @JsonProperty("protected")
    private boolean protection;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public void forEach(Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'forEach'");
    }
}