package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workflow")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
// https://docs.github.com/en/rest/actions/workflows?apiVersion=2022-11-28#get-a-workflow
public class Workflow extends BaseGitServiceEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepository repository;

    private String name;

    private String path;

    // Custom field
    private String fileNameWithExtension;

    @NonNull
    @Enumerated(EnumType.STRING)
    private State state;

    private String url;

    private String htmlUrl;

    private String badgeUrl;

    // Custom field for Repository Settings
    @NonNull
    @Enumerated(EnumType.STRING)
    private Label label = Label.NONE;

    public enum State {
        ACTIVE,
        DELETED,
        DISABLED_FORK,
        DISABLED_INACTIVITY,
        DISABLED_MANUALLY,
        UNKNOWN,
    }

    public enum Label {
        BUILD,
        DEPLOYMENT,
        NONE
    }
}
