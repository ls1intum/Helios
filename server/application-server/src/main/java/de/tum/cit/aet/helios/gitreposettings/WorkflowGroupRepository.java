package de.tum.cit.aet.helios.gitreposettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface WorkflowGroupRepository extends JpaRepository<WorkflowGroup, Long> {

    // Find all WorkflowGroups by Repository ID
    @Query("SELECT wg " +
            "FROM WorkflowGroup wg " +
            "WHERE wg.gitRepoSettings.repository.id = :repositoryId " +
            "ORDER BY wg.orderIndex ASC")
    List<WorkflowGroup> findAllByRepositoryId(@Param("repositoryId") Long repositoryId);

    // Find all WorkflowGroups by GitRepoSettings
    @Query("SELECT wg FROM WorkflowGroup wg WHERE wg.gitRepoSettings = :gitRepoSettings ORDER BY wg.orderIndex ASC")
    List<WorkflowGroup> findAllByGitRepoSettings(@Param("gitRepoSettings") GitRepoSettings gitRepoSettings);

    // Find the latest order index for a given GitRepoSettings
    // If no order index is found, return -1
    @Query("SELECT COALESCE(MAX(wg.orderIndex), -1) " +
            "FROM WorkflowGroup wg " +
            "WHERE wg.gitRepoSettings = :gitRepoSettings")
    Optional<Integer> findLatestOrderIndexByGitRepoSettings(@Param("gitRepoSettings") GitRepoSettings gitRepoSettings);

    // Decrement order indexes by 1 after a deleted order index
    @Modifying
    @Query("UPDATE WorkflowGroup wg " +
            "SET wg.orderIndex = wg.orderIndex - 1 " +
            "WHERE wg.gitRepoSettings = :gitRepoSettings " +
            "AND wg.orderIndex > :deletedOrderIndex")
    void decrementOrderIndexesAfter(@Param("deletedOrderIndex") int deletedOrderIndex,
                                    @Param("gitRepoSettings") GitRepoSettings gitRepoSettings);

}