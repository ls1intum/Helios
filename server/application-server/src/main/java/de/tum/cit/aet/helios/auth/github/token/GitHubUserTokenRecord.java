package de.tum.cit.aet.helios.auth.github.token;

import java.time.OffsetDateTime;

/**
 * Immutable snapshot of a user's GitHub tokens with absolute expiry instants. Carried from the
 * seed (Keycloak retrieve-token) and refresh (GitHub) clients into {@link GitHubUserTokenService}.
 *
 * <p>{@code refreshToken} / {@code refreshTokenExpiresAt} may be {@code null} when GitHub did not
 * issue a refresh token (e.g. the App has token expiration disabled).
 */
public record GitHubUserTokenRecord(
    String accessToken,
    OffsetDateTime accessTokenExpiresAt,
    String refreshToken,
    OffsetDateTime refreshTokenExpiresAt) {}
