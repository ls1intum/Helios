package de.tum.cit.aet.helios.releasecandidate;

import de.tum.cit.aet.helios.commit.CommitInfoDto;
import java.util.List;
import org.springframework.lang.NonNull;

public record CommitsSinceReleaseCandidateDto(
    @NonNull Integer commitsLength, @NonNull List<CommitInfoDto> commits) {}
