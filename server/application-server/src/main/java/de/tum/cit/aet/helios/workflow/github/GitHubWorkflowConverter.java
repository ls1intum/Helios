package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.workflow.Workflow;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHWorkflow;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Log4j2
@Component
public class GitHubWorkflowConverter extends BaseGitServiceEntityConverter<GHWorkflow, Workflow> {

    @Override
    public Workflow convert(@NonNull GHWorkflow source) {
        return update(source, new Workflow());
    }

    @Override
    public Workflow update(@NonNull GHWorkflow source, @NonNull Workflow workflow) {
        convertBaseFields(source, workflow);
        workflow.setName(source.getName());

        final String path = source.getPath();
        workflow.setPath(path);

        // GitHub Actions workflows are stored in the ".github/workflows" directory.
        // Therefore, the path will always start with ".github/workflows/".
        // We can extract the file name by splitting the path by '/' and taking the last element.
        // Refer to: https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions#about-yaml-syntax-for-workflows
        try {
            final String[] pathParts = path.split("/");
            workflow.setFileNameWithExtension(pathParts[pathParts.length - 1]);
        } catch (Exception e) {
            log.error("Failed to extract file name from path: {} for source {}: {}", path, source.getId(), e.getMessage());
            workflow.setFileNameWithExtension(path);
        }

        workflow.setState(convertStatus(source.getState()));

        workflow.setUrl(source.getUrl().toString());

        try {
            workflow.setHtmlUrl(source.getHtmlUrl().toString());
        } catch (IOException e) {
            log.error("Failed to convert htmlUrl field for source {}: {}", source.getId(), e.getMessage());
        }
        workflow.setBadgeUrl(source.getBadgeUrl().toString());

        return workflow;
    }

    private Workflow.State convertStatus(String status) {
        return switch (status) {
            case "active" -> Workflow.State.ACTIVE;
            case "deleted" -> Workflow.State.DELETED;
            case "disabled_fork" -> Workflow.State.DISABLED_FORK;
            case "disabled_inactivity" -> Workflow.State.DISABLED_INACTIVITY;
            case "disabled_manually" -> Workflow.State.DISABLED_MANUALLY;
            default -> Workflow.State.UNKNOWN;
        };
    }
}
