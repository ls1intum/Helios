package de.tum.cit.aet.helios;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Helios API", 
        description = "API documentation for the Helios application server.", 
        version = "0.0.1",
        contact = @Contact(
            name = "Turker Koc",
            email = "turker.koc@tum.de"
        ), 
        license = @License(
            name = "MIT License",
            url = "https://github.com/ls1intum/Helios/blob/main/LICENSE"
        )
    ), 
    servers = {
        @Server(url = "/", description = "Default Server URL"),
    }
)
public class OpenAPIConfiguration {

}