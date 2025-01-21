package de.tum.cit.aet.helios;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import de.tum.cit.aet.helios.e2e.HeliosPlaywrightTest;
import org.junit.jupiter.api.Test;

public class ListRepositoryPageTest extends HeliosPlaywrightTest {
  @Test
  public void testPageIsRendering() {
    page.navigate(this.baseUrl);
    assertThat(page).hasURL(this.baseUrl + "/repo/list");
    assertThat(page.getByText("Connected Repositories")).isVisible();
  }
}
