package de.tum.cit.aet.helios.config.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * This annotation is used to enforce that the user has write permission. It should only be used
 * with endpoints starting with {@code /api/}
 *
 * <p>It's only addable to methods. The intention is that a developer can see the required role
 * without the need to scroll up. This also prevents overrides of the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('WRITE') or hasRole('MAINTAINER') or hasRole('ADMIN')")
public @interface EnforceAtLeastWritePermission {}
