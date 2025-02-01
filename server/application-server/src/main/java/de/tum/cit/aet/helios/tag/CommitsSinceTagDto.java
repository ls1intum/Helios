package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.commit.CommitInfoDto;
import java.util.List;
import org.springframework.lang.NonNull;

public record CommitsSinceTagDto(
    @NonNull Integer commitsLength, @NonNull List<CommitInfoDto> commits) {}
