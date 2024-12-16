package de.tum.cit.aet.helios.environment;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    public Optional<EnvironmentDTO> getEnvironmentById(Long id) {
        return environmentRepository.findById(id)
                .map(EnvironmentDTO::fromEnvironment);
    }

    public List<EnvironmentDTO> getAllEnvironments() {
        return environmentRepository.findAll()
                .stream()
                .map(EnvironmentDTO::fromEnvironment)
                .collect(Collectors.toList());
    }

    public List<EnvironmentDTO> getEnvironmentsByRepositoryId(Long repositoryId) {
        return environmentRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId)
                .stream()
                .map(EnvironmentDTO::fromEnvironment)
                .collect(Collectors.toList());
    }

    public Optional<EnvironmentDTO> updateEnvironment(Long id, EnvironmentDTO environmentDTO) {
        return environmentRepository.findById(id).map(environment -> {
            if (environmentDTO.name() != null) {
                environment.setName(environmentDTO.name());
            }
            if (environmentDTO.url() != null) {
                environment.setUrl(environmentDTO.url());
            }
            if (environmentDTO.htmlUrl() != null) {
                environment.setHtmlUrl(environmentDTO.htmlUrl());
            }
            if (environmentDTO.createdAt() != null) {
                environment.setCreatedAt(environmentDTO.createdAt());
            }
            if (environmentDTO.updatedAt() != null) {
                environment.setUpdatedAt(environmentDTO.updatedAt());
            }
            if (environmentDTO.installedApps() != null) {
                environment.setInstalledApps(environmentDTO.installedApps());
            }
            environmentRepository.save(environment);
            return EnvironmentDTO.fromEnvironment(environment);
        });
    }

    public Optional<EnvironmentDTO> updateInstalledApps(Long id, List<String> installedApps) {
        return environmentRepository.findById(id).map(environment -> {
            environment.setInstalledApps(installedApps);
            environmentRepository.save(environment);
            return EnvironmentDTO.fromEnvironment(environment);
        });
    }

}
