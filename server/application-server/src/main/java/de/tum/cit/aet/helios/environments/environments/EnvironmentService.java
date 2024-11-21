package de.tum.cit.aet.helios.environments.environments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    @Autowired
    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    public List<EnvironmentInfoDTO> getAllEnvironemnts() {
        return environmentRepository.findAll().stream()
                .map(EnvironmentInfoDTO::fromEnvironment)
                .collect(Collectors.toList());
    }

    public Optional<EnvironmentInfoDTO> getEnvironmentById(Long id) {
        return environmentRepository.findById(id)
                .map(EnvironmentInfoDTO::fromEnvironment);
    }

    // public EnvironmentInfoDTO createEnvironment(EnvironmentInfoDTO environmentInfoDTO) {
    //     EnvironmentInfo environmentInfo = EnvironmentInfo(environmentInfoDTO);
    //     return EnvironmentInfoDTO.fromEnvironment(environmentRepository.save(environmentInfo));
    // }
}