package de.tum.cit.aet.helios.gitreposettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkflowGroupMembershipRepository extends JpaRepository<WorkflowGroupMembership, Long> {

    /**
     * Deletes all membership rows where the workflow's repository id matches the given repositoryId.
     */
    @Modifying
    @Query("DELETE FROM WorkflowGroupMembership m " +
            "WHERE m.workflow.repository.id = :repositoryId")
    void deleteAllByRepositoryId(@Param("repositoryId") Long repositoryId);
}