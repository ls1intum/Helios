package de.tum.cit.aet.helios;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;

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