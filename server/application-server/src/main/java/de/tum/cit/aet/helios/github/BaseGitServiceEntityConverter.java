package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.common.util.DateUtil;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHObject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
@Log4j2
public abstract class BaseGitServiceEntityConverter<
        S extends GHObject, T extends BaseGitServiceEntity>
    implements Converter<S, T> {

  public abstract T update(@NonNull S source, @NonNull T target);

  protected void convertBaseFields(S source, T target) {
    if (source == null || target == null) {
      throw new IllegalArgumentException("Source and target must not be null");
    }

    // Map common fields
    target.setId(source.getId());

    try {
      target.setCreatedAt(DateUtil.convertToOffsetDateTime(source.getCreatedAt()));
    } catch (IOException e) {
      log.error(
          "Failed to convert createdAt field for source {}: {}", source.getId(), e.getMessage());
      target.setCreatedAt(null);
    }

    try {
      target.setUpdatedAt(DateUtil.convertToOffsetDateTime(source.getUpdatedAt()));
    } catch (IOException e) {
      log.error(
          "Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
      target.setUpdatedAt(null);
    }
  }
}
