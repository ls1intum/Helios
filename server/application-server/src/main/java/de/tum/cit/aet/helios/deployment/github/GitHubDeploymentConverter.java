package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.GitHubDeployment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubDeploymentConverter implements Converter<GitHubDeployment, Deployment> {

    @Override
    public Deployment convert(@NonNull GitHubDeployment source) {
        return update(source, new Deployment());
    }

    public Deployment update(@NonNull GitHubDeployment source, @NonNull Deployment deployment) {
        deployment.setId(source.getId());
        deployment.setNodeId(source.getNodeId());
        deployment.setSha(source.getSha());
        deployment.setRef(source.getRef());
        deployment.setTask(source.getTask());

        // Convert JsonNode payload to String or Map?
        if (source.getPayload() != null) {
            deployment.setPayload(source.getPayload().toString());
        } else {
            deployment.setPayload(null);
        }

        deployment.setEnvironment(source.getEnvironment());
        deployment.setOriginalEnvironment(source.getOriginalEnvironment());
        deployment.setDescription(source.getDescription());
        deployment.setCreatedAt(source.getCreatedAt());
        deployment.setUpdatedAt(source.getUpdatedAt());

        return deployment;
    }
}
