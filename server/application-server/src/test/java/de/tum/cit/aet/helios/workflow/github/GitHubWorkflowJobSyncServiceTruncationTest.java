package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitHubWorkflowJobSyncServiceTruncationTest {

  private static final int COLUMN_LENGTH = 255;

  @Test
  void keepsNullAndShortValuesUntouched() {
    assertNull(GitHubWorkflowJobSyncService.truncate(null, "name", 1L));
    String shortName = "Build / Build .war artifact";
    assertSame(shortName, GitHubWorkflowJobSyncService.truncate(shortName, "name", 1L));
  }

  @Test
  void keepsAValueOfExactlyTheColumnLength() {
    String exact = "a".repeat(COLUMN_LENGTH);
    assertSame(exact, GitHubWorkflowJobSyncService.truncate(exact, "name", 1L));
  }

  @Test
  void cutsALongerValueToTheColumnLengthAndKeepsThePrefix() {
    String expression =
        "github.event.action == 'edited' && !github.event.changes.base && "
            + "'PR metadata updated (validation unchanged)' || 'x'".repeat(10);
    assertTrue(expression.length() > COLUMN_LENGTH);

    String result = GitHubWorkflowJobSyncService.truncate(expression, "name", 1L);

    assertEquals(COLUMN_LENGTH, result.length());
    assertTrue(expression.startsWith(result));
  }

  @Test
  void doesNotSplitASurrogatePairAtTheCut() {
    // 254 plain characters, then a supplementary character (two chars) that straddles the limit.
    String value = "a".repeat(COLUMN_LENGTH - 1) + "😀" + "tail";

    String result = GitHubWorkflowJobSyncService.truncate(value, "name", 1L);

    assertEquals(COLUMN_LENGTH - 1, result.length());
    assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)));
  }
}
