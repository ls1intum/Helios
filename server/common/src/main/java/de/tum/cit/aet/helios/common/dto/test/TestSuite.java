package de.tum.cit.aet.helios.common.dto.test;

import java.time.LocalDateTime;
import java.util.List;

public record TestSuite(
    String name,
    LocalDateTime timestamp,
    Integer tests,
    Integer failures,
    Integer errors,
    Integer skipped,
    Double time,
    List<TestCase> testCases) {}
