package de.tum.cit.aet.helios.workflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelSetsTest {

  @Test
  void canonicalLowercasesAndSorts() {
    assertEquals(List.of("alpha", "beta"), LabelSets.canonical(List.of("Beta", "ALPHA")));
  }

  @Test
  void canonicalDropsBlanks() {
    assertEquals(List.of("foo"), LabelSets.canonical(Arrays.asList("foo", "", "  ", null)));
  }

  @Test
  void hashIsStableForEqualSets() {
    assertEquals(
        LabelSets.hash(List.of("self-hosted", "linux")),
        LabelSets.hash(List.of("linux", "Self-Hosted")));
  }

  @Test
  void hashIsDifferentForDifferentSets() {
    assertNotEquals(
        LabelSets.hash(List.of("self-hosted", "linux")),
        LabelSets.hash(List.of("self-hosted", "windows")));
  }

  /** Guards the "abc" collision found in deep review: empty separator joins like sets. */
  @Test
  void hashDoesNotCollideForAdjacencyBoundary() {
    assertNotEquals(LabelSets.hash(List.of("a", "bc")), LabelSets.hash(List.of("ab", "c")));
  }

  @Test
  void runnerKindDerivedFromSelfHosted() {
    assertEquals(
        WorkflowJob.RunnerKind.SELF_HOSTED,
        LabelSets.deriveRunnerKind(List.of("self-hosted", "linux")));
  }

  @Test
  void runnerKindDerivedFromUbuntuLatest() {
    assertEquals(
        WorkflowJob.RunnerKind.GITHUB_HOSTED,
        LabelSets.deriveRunnerKind(List.of("ubuntu-latest")));
  }

  @Test
  void runnerKindDerivedFromUbuntuPrefix() {
    assertEquals(
        WorkflowJob.RunnerKind.GITHUB_HOSTED,
        LabelSets.deriveRunnerKind(List.of("ubuntu-22.04")));
  }

  @Test
  void runnerKindUnknownForEmpty() {
    assertEquals(WorkflowJob.RunnerKind.UNKNOWN, LabelSets.deriveRunnerKind(List.of()));
    assertEquals(WorkflowJob.RunnerKind.UNKNOWN, LabelSets.deriveRunnerKind(null));
  }

  @Test
  void hashHasFixedWidth() {
    // SHA-256 hex = 64 characters (changed from SHA-1's 40 to satisfy static analysis).
    assertTrue(LabelSets.hash(List.of("anything")).matches("[0-9a-f]{64}"));
  }
}
