package de.tum.cit.aet.helios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

/**
 * One target that accepts Helios status JSON over HTTP POST.
 *
 * @param url absolute URI of the Helios endpoint
 * @param secretKey shared secret for simple token auth
 */
public record HeliosEndpoint(
    @NotNull URI url,
    @NotBlank String secretKey) {
}
