package de.tum.cit.aet.helios.branch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.User;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean isProtected;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToOne
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private GitRepository repository;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}