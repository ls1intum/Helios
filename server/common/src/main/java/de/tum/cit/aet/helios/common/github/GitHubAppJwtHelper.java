package de.tum.cit.aet.helios.common.github;

import io.jsonwebtoken.Jwts;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Helper class to generate a JWT for GitHub App authentication. */
public class GitHubAppJwtHelper {

  /** Reads a PEM file (PKCS#8) from disk and returns the PrivateKey. */
  public static PrivateKey loadPrivateKey(File pemFile) throws Exception {
    // Generated PEM in GitHub App settings is in PKCS#1 format
    // Convert it to PKCS#8 format using the following command:
    // openssl pkcs8 -topk8 -nocrypt \
    //    -in original_key.pem \
    //    -out converted_key_pkcs8.pem

    // Read the PEM file
    String pem = Files.readString(pemFile.toPath(), StandardCharsets.UTF_8);
    // Remove the "BEGIN", "END" and newlines
    pem =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

    // Decode
    byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pem);
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);

    // Create a private key from the key spec
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePrivate(keySpec);
  }

  /**
   * Generates a JWT valid for up to 9 minutes to authenticate as a GitHub App (server-to-server).
   *
   * @param appId The numeric GitHub App ID
   * @param privateKey The RSA PrivateKey
   */
  public static String generateAppJwt(long appId, PrivateKey privateKey) {
    Instant now = Instant.now();
    // 9 minutes from now
    Instant exp = now.plusSeconds(9 * 60);

    return Jwts.builder()
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .issuer(String.valueOf(appId))
        .signWith(privateKey)
        .compact();
  }
}
