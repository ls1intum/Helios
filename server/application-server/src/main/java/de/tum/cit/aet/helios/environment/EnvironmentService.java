package de.tum.cit.aet.helios.environment;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.helios.branch.Branch;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

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

    /**
     * Locks the environment with the specified ID.
     * <p>
     * This method attempts to lock the environment by setting its locked status to true.
     * If the environment is already locked, it returns an empty Optional.
     * If the environment is successfully locked, it returns an Optional containing the locked environment.
     * <p>
     * This method is transactional and handles optimistic locking failures.
     *
     * @param id the ID of the environment to lock
     * @return an Optional containing the locked environment if successful, or an empty Optional if the environment
     *         is already locked or if an optimistic locking failure occurs
     * @throws EntityNotFoundException if no environment is found with the specified ID
     */
    @Transactional
    public Optional<Environment> lockEnvironment(Long id, Branch branch) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

        if (environment.isLocked()) {
            if (environment.getLockingBranch().equals(branch)) {
                // The environment is already locked by the same branch
                return Optional.of(environment);
            }

            return Optional.empty();
        }

        environment.setLockingBranch(branch);
        environment.setDeploying(true);

        try {
            environmentRepository.save(environment);
        } catch (OptimisticLockingFailureException e) {
            // The environment was locked by another transaction
            return Optional.empty();
        }

        return Optional.of(environment);
    }

    /**
     * Unlocks the environment with the specified ID.
     * 
     * This method sets the locked status of the environment to false and saves the updated environment.
     * 
     * @param id the ID of the environment to unlock
     * @throws EntityNotFoundException if no environment is found with the specified ID
     */
    @Transactional
    public EnvironmentDTO unlockEnvironment(Long id) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

        environment.setLockingBranch(null);
        environment.setDeploying(false);

        environmentRepository.save(environment);

        return EnvironmentDTO.fromEnvironment(environment);
    }
}
