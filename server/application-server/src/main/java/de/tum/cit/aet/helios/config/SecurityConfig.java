package de.tum.cit.aet.helios.config;

import static org.springframework.security.config.Customizer.withDefaults;

import de.tum.cit.aet.helios.filters.RepoSecretFilter;
import de.tum.cit.aet.helios.filters.RepositoryInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Log4j2
@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final Environment environment;
  private final GitHubJwtAuthenticationConverter gitHubJwtAuthenticationConverter;
  private final RepoSecretFilter repoSecretFilter;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(withDefaults()) // Enable CORS
        .csrf(AbstractHttpConfigurer::disable) // Disable CSRF
        .authorizeHttpRequests(
            auth -> {
              auth
                  /* public GET endpoints */
                  .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                  /* shared‑secret filter handles these requests */
                  .requestMatchers(HttpMethod.POST, "/api/environments/status/**").permitAll()
                  /* other public endpoints (e.g. Swagger) */
                  .requestMatchers(
                      "/auth/**",
                      "/bus/v3/api-docs/**",
                      "/v2/api-docs",
                      "/v3/api-docs",
                      "/v3/api-docs/**",
                      "/v3/api-docs.yaml",
                      "/swagger-resources",
                      "/swagger-resources/**",
                      "/configuration/ui",
                      "/configuration/security",
                      "/swagger-ui/**",
                      "/webjars/**",
                      "/status/health",
                      "/swagger-ui.html").permitAll()
                  /* Everything else needs Keycloak authentication */
                  .anyRequest().authenticated();
            })
        /* Custom filter for shared secrets */
        .addFilterBefore(repoSecretFilter, UsernamePasswordAuthenticationFilter.class)

        /* Keycloak authentication */
        .oauth2ResourceServer(
            auth ->
                auth.jwt(
                    token -> token.jwtAuthenticationConverter(gitHubJwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) ->
        web.ignoring()
            .requestMatchers(
                "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html");
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {

    return new WebMvcConfigurer() {
      @Autowired
      private RepositoryInterceptor requestInterceptor;

      @Override
      public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addWebRequestInterceptor(requestInterceptor);
      }

      @Override
      public void addCorsMappings(@NotNull CorsRegistry registry) {
        if (environment.matchesProfiles("prod")) {
          // Allow production domain
          registry
              .addMapping("/api/**")
              .allowedOrigins("https://helios.aet.cit.tum.de")
              .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
              .allowedHeaders("*")
              .allowCredentials(true);
        } else if (environment.matchesProfiles("staging")) {
          // Allow staging domain
          registry
              .addMapping("/api/**")
              .allowedOrigins("https://helios-staging.aet.cit.tum.de")
              .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
              .allowedHeaders("*")
              .allowCredentials(true);
        } else {
          log.info("Allowing localhost for development.");
          // Allow localhost during development
          registry
              .addMapping("/api/**")
              .allowedOriginPatterns(
                  "http://localhost",
                  "http://localhost:*",
                  "http://127.0.0.1",
                  "http://127.0.0.1:*")
              .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
              .allowedHeaders("*")
              .allowCredentials(true);
        }
      }
    };
  }
}
