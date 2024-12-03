package de.tum.cit.aet.helios.environment.github;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.GitHubEnvironment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;

@Component
public class GitHubEnvironmentConverter implements Converter<GitHubEnvironment, Environment> {

    @Override
    public Environment convert(@NonNull GitHubEnvironment source) {
        return update(source, new Environment());
    }

    public Environment update(@NonNull GitHubEnvironment source, @NonNull Environment environment) {
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
