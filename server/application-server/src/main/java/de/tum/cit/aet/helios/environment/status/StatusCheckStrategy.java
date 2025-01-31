package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;

public interface StatusCheckStrategy {
    StatusCheckResult check(Environment environment);
}
