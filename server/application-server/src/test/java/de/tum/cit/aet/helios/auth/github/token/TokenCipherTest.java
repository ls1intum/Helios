package de.tum.cit.aet.helios.auth.github.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

  // A valid 256-bit AES key (all-zero bytes is fine for a round-trip test).
  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void encryptThenDecryptRoundTrips() {
    TokenCipher cipher = new TokenCipher(KEY);
    String encrypted = cipher.encrypt("ghr_secret_refresh_token");
    assertNotEquals("ghr_secret_refresh_token", encrypted);
    assertEquals("ghr_secret_refresh_token", cipher.decrypt(encrypted));
  }

  @Test
  void encryptUsesRandomIvSoCiphertextsDiffer() {
    TokenCipher cipher = new TokenCipher(KEY);
    assertNotEquals(cipher.encrypt("same-plaintext"), cipher.encrypt("same-plaintext"));
  }

  @Test
  void blankKeyDoesNotFailConstructionButFailsOnUse() {
    // A missing key must not crash app startup; it fails only when a token op is attempted.
    TokenCipher cipher = new TokenCipher("  ");
    assertThrows(IllegalStateException.class, () -> cipher.encrypt("x"));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("aaa:bbb"));
  }
}
