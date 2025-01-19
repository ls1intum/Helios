package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest()
@Tag("playwright")
public class TrivialPlaywrightTestE2E {

  // This will be injected with the random free port
  // number that was allocated
  private final int port = 4200;

  static Playwright playwright = Playwright.create();

  @Test
  public void testClicking() {
    assertEquals(true, false);
    Browser browser = playwright.chromium().launch();
    Page page = browser.newPage();
    page.navigate("http://localhost:" + port + "/");
  }
}
