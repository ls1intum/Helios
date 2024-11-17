package de.tum.cit.aet.helios.gitprovider.common.github;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class DateUtil {
    public static OffsetDateTime convertToOffsetDateTime(Date date) {
        return date != null ? date.toInstant().atOffset(ZoneOffset.UTC) : null;
    }
}
