package de.tum.cit.aet.helios.common.dto.test;

public record TestCase(
    String name,
    String className,
    Double time,
    boolean failed,
    boolean error,
    boolean skipped,
    String errorType,
    String message,
    String stackTrace) {}
