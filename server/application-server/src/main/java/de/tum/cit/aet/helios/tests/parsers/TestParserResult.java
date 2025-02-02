package de.tum.cit.aet.helios.tests.parsers;

public record TestParserResult(int total, int failures, int errors, int skipped, double time) {
    public TestParserResult {
        if (total < 0 || failures < 0 || errors < 0 || skipped < 0 || time < 0) {
            throw new IllegalArgumentException("Negative values not allowed");
        }
    }

    public int passed() {
        return total - failures - errors - skipped;
    }
}
