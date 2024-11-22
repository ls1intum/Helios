package de.tum.cit.aet.helios.deployment.environments;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    @Autowired
    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    public List<EnvironmentInfoDTO> getAllEnvironments() {
        return environmentRepository.findAll().stream()
                .map(EnvironmentInfoDTO::fromEnvironmentInfo)
                .collect(Collectors.toList());
    }

    public Optional<EnvironmentInfoDTO> getEnvironmentById(Long id) {
        return environmentRepository.findById(id)
                .map(EnvironmentInfoDTO::fromEnvironmentInfo);
    }

    public EnvironmentInfoDTO createEnvironment(EnvironmentInfoDTO environmentInfoDTO) {
        EnvironmentInfo environmentInfo = new EnvironmentInfo(environmentInfoDTO);
        return EnvironmentInfoDTO.fromEnvironmentInfo(environmentRepository.save(environmentInfo));
    }
}