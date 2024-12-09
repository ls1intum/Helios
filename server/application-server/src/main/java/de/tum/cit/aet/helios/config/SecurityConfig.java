package de.tum.cit.aet.helios.config;

import static org.springframework.security.config.Customizer.withDefaults;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Log4j2
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults()) // Enable CORS
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF
                .authorizeHttpRequests(auth -> {

                            auth.requestMatchers("/api/**").permitAll();  // Allow API access

                            if (environment.matchesProfiles("prod")) {
                                auth.anyRequest().denyAll(); // Deny all other requests
                            } else {
                                log.info("Allowing OpenAPI and Swagger UI endpoints for development.");
                                // Allow access to OpenAPI and Swagger UI
                                auth
                                        .requestMatchers("/bus/v3/api-docs/**").permitAll()
                                        .requestMatchers("/v3/api-docs/**").permitAll()
                                        .requestMatchers("/v3/api-docs.yaml").permitAll()
                                        .requestMatchers("/swagger-ui/**").permitAll()
                                        .requestMatchers("/swagger-ui.html").permitAll();

                            }
                        }
                );

        return http.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                if (environment.matchesProfiles("prod")) {
                    // Allow production domain
                    registry.addMapping("/api/**")
                            .allowedOrigins("https://helios.artemis.cit.tum.de")
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                            .allowedHeaders("*")
                            .allowCredentials(true);
                } else {
                    log.info("Allowing localhost for development.");
                    // Allow localhost during development
                    registry.addMapping("/api/**")
                            .allowedOriginPatterns("http://localhost", "http://localhost:*", "http://127.0.0.1", "http://127.0.0.1:*")
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                            .allowedHeaders("*")
                            .allowCredentials(true);
                }
            }
        };
    }
}