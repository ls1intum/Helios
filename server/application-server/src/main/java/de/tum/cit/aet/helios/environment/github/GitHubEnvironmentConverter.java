package de.tum.cit.aet.helios.environment.github;

import de.tum.cit.aet.helios.environment.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;

@Component
public class GitHubEnvironmentConverter implements Converter<GitHubEnvironmentDTO, Environment> {

    @Override
    public Environment convert(@NonNull GitHubEnvironmentDTO source) {
        return update(source, new Environment());
    }

    public Environment update(@NonNull GitHubEnvironmentDTO source, @NonNull Environment environment) {
        environment.setId(source.getId());
        environment.setName(source.getName());
        environment.setUrl(source.getUrl());
        environment.setHtmlUrl(source.getHtmlUrl());
        environment.setCreatedAt(source.getCreatedAt());
        environment.setUpdatedAt(source.getUpdatedAt());

        // The repository field will be set separately in the sync service
        return environment;
    }
}
