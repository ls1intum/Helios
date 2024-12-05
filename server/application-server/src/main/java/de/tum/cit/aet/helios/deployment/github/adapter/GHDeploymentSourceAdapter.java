package de.tum.cit.aet.helios.deployment.github.adapter;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import org.kohsuke.github.GHDeployment;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;


public class GHDeploymentSourceAdapter implements DeploymentSource {

    private final GHDeployment ghDeployment;

    public GHDeploymentSourceAdapter(GHDeployment ghDeployment) {
        this.ghDeployment = ghDeployment;
    }

    @Override
    public Long getId() {
        return ghDeployment.getId();
    }

    @Override
    public String getNodeId() {
        return ghDeployment.getNodeId();
    }

    @Override
    public String getUrl() {
        return ghDeployment.getUrl().toString();
    }

    @Override
    public String getStatusesUrl() {
        return ghDeployment.getStatusesUrl().toString();
    }

    @Override
    public String getSha() {
        return ghDeployment.getSha();
    }

    @Override
    public String getRef() {
        return ghDeployment.getRef();
    }

    @Override
    public String getTask() {
        return ghDeployment.getTask();
    }

    @Override
    public String getEnvironment() {
        return ghDeployment.getEnvironment();
    }

    @Override
    public String getRepositoryUrl() {
        return ghDeployment.getRepositoryUrl().toString();
    }

    @Override
    public OffsetDateTime getCreatedAt() throws IOException {
        return ghDeployment.getCreatedAt().toInstant().atOffset(OffsetDateTime.now().getOffset());
    }

    @Override
    public OffsetDateTime getUpdatedAt() throws IOException {
        return ghDeployment.getUpdatedAt().toInstant().atOffset(OffsetDateTime.now().getOffset());
    }
}
