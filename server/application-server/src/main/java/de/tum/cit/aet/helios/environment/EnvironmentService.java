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

}
