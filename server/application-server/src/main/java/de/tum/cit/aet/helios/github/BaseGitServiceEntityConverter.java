package de.tum.cit.aet.helios.github;

import org.kohsuke.github.GHObject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

import de.tum.cit.aet.helios.util.DateUtil;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

@ReadingConverter
@Log4j2
public abstract class BaseGitServiceEntityConverter<S extends GHObject, T extends BaseGitServiceEntity>
        implements Converter<S, T> {

    abstract public T update(@NonNull S source, @NonNull T target);

    protected void convertBaseFields(S source, T target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target must not be null");
        }

        // Map common fields
        target.setId(source.getId());

        try {
            target.setCreatedAt(DateUtil.convertToOffsetDateTime(source.getCreatedAt()));
        } catch (IOException e) {
            log.error("Failed to convert createdAt field for source {}: {}", source.getId(), e.getMessage());
            target.setCreatedAt(null);
        }

        try {
            target.setUpdatedAt(DateUtil.convertToOffsetDateTime(source.getUpdatedAt()));
        } catch (IOException e) {
            log.error("Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
            target.setUpdatedAt(null);
        }
    }
}