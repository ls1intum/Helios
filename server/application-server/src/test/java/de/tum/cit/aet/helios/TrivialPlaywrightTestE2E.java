package de.tum.cit.aet.helios;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest()
@Tag("playwright")
@AutoConfigureEmbeddedDatabase
public class TrivialPlaywrightTestE2E {

  // This will be injected with the random free port
  // number that was allocated
  private final int port = 4200;

  static Playwright playwright = Playwright.create();

  @Test
  public void testClicking() {
    Browser browser = playwright.chromium().launch();
    Page page = browser.newPage();
    page.navigate("http://localhost:" + port + "/");
  }
}
