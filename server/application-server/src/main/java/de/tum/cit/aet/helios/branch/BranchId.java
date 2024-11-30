package de.tum.cit.aet.helios.branch;

import java.io.Serializable;
import java.util.Objects;

public class BranchId implements Serializable {
    private String name;
    private Long repository;

    // Default constructor
    public BranchId() {}

    public BranchId(String name, Long repository) {
        this.name = name;
        this.repository = repository;
    }

    // Equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BranchId branchId = (BranchId) o;
        return Objects.equals(name, branchId.name) &&
               Objects.equals(repository, branchId.repository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, repository);
    }
}