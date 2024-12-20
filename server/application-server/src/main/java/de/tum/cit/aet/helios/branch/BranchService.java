package de.tum.cit.aet.helios.branch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<BranchInfoDTO> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(BranchInfoDTO::fromBranch)
                .collect(Collectors.toList());
    }

    public Optional<BranchInfoDTO> getBranchById(Long id) {
        return branchRepository.findById(id)
                .map(BranchInfoDTO::fromBranch);
    }

    @Transactional
    public void deleteBranchByNameAndRepositoryId(String name, Long repositoryId) {
        branchRepository.deleteByNameAndRepositoryId(name, repositoryId);
    }

    public List<BranchInfoDTO> getBranchesByRepositoryId(Long repositoryId) {
        return branchRepository.findByRepositoryId(repositoryId).stream()
                .map(BranchInfoDTO::fromBranch)
                .collect(Collectors.toList());
    }
}