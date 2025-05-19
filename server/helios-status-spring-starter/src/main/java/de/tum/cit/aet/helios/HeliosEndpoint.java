package de.tum.cit.aet.helios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

/**
 * Immutable configuration record representing a single Helios endpoint.
 *
 * <p>This record is used to define where status updates should be pushed.
 * Each endpoint requires a URL (the HTTP POST target) and a shared secret
 * used for authentication.</p>
 *
 * @param url absolute URI of the Helios ingestion endpoint; must not be null
 * @param secretKey shared secret for HTTP token authentication; must not be blank
 */
public record HeliosEndpoint(
    @NotNull URI url,
    @NotBlank String secretKey) {
}
