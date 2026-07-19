package de.tum.cit.aet.helios.auth.github.token;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption for GitHub tokens persisted at rest. The stored form is
 * {@code base64(iv):base64(ciphertext+tag)} with a fresh random IV per call.
 *
 * <p>The key comes from {@code helios.tokenEncryptionKey} (env
 * {@code HELIOS_TOKEN_ENCRYPTION_KEY}) — a base64-encoded 128/192/256-bit AES key. A blank key
 * fails fast at construction so GitHub refresh tokens (6-month credentials) are never written in
 * plaintext.
 */
@Component
public class TokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public TokenCipher(@Value("${helios.tokenEncryptionKey:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException(
          "helios.tokenEncryptionKey (HELIOS_TOKEN_ENCRYPTION_KEY) must be set to a base64 AES key "
              + "so GitHub tokens are never stored in plaintext.");
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  /** Encrypts {@code plaintext}, returning {@code base64(iv):base64(ciphertext)}. */
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      Base64.Encoder encoder = Base64.getEncoder();
      return encoder.encodeToString(iv) + ":" + encoder.encodeToString(ciphertext);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt token", e);
    }
  }

  /** Reverses {@link #encrypt(String)}. */
  public String decrypt(String stored) {
    int separator = stored.indexOf(':');
    if (separator < 0) {
      throw new IllegalArgumentException("Malformed encrypted token (missing IV separator)");
    }
    try {
      Base64.Decoder decoder = Base64.getDecoder();
      byte[] iv = decoder.decode(stored.substring(0, separator));
      byte[] ciphertext = decoder.decode(stored.substring(separator + 1));
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt token", e);
    }
  }
}
