package de.tum.cit.aet.helios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

public record HeliosEndpoint(
    @NotNull URI url,
    @NotBlank String secretKey) {
}
