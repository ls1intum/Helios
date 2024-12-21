package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Log4j2
@Component
public class DeploymentConverter implements Converter<DeploymentSource, Deployment> {

    @Override
    public Deployment convert(@NonNull DeploymentSource source) {
        return update(source, new Deployment());
    }

    public Deployment update(@NonNull DeploymentSource source, @NonNull Deployment deployment) {
        deployment.setId(source.getId());
        deployment.setNodeId(source.getNodeId());
        deployment.setUrl(source.getUrl());
        deployment.setState(source.getState());
        deployment.setStatusesUrl(source.getStatusesUrl());
        deployment.setSha(source.getSha());
        deployment.setRef(source.getRef());
        deployment.setTask(source.getTask());
        deployment.setEnvironmentName(source.getEnvironment());
        deployment.setRepositoryUrl(source.getRepositoryUrl());
        try {
            deployment.setCreatedAt(source.getCreatedAt());
        } catch (IOException e) {
            log.error("Error while converting deployment source to deployment, setting createdAt", e);
        }
        try {
            deployment.setUpdatedAt(source.getUpdatedAt());
        } catch (IOException e) {
            log.error("Error while converting deployment source to deployment, setting updatedAt", e);
        }

        return deployment;
    }
}
