package de.tum.cit.aet.helios;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Helios API",
            description = "API documentation for the Helios application server.",
            version = "0.0.1",
            contact = @Contact(name = "Turker Koc", email = "turker.koc@tum.de"),
            license =
                @License(
                    name = "MIT License",
                    url = "https://github.com/ls1intum/Helios/blob/main/LICENSE")),
    servers = {
      @Server(url = "/", description = "Default Server URL"),
    })
public class OpenApiConfiguration {}
