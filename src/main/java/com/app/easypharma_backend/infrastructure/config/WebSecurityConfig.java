package com.app.easypharma_backend.infrastructure.config;

import com.app.easypharma_backend.infrastructure.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtFilter jwtFilter;

    // Origines autorisées : app.cors.allowed-origins (déjà utilisée par les contrôleurs). En production, la définir par
    // l'environnement (APP_CORS_ALLOWED_ORIGINS) avec l'adresse réelle du frontend.
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    // Console H2 : seulement si elle est activée par la configuration (jamais par défaut).
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    // Métriques lisibles sans jeton : profil observability seulement.
    @Value("${app.security.public-metrics:false}")
    private boolean publicMetrics;

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                    org.springframework.security.core.AuthenticationException authException)
                    throws IOException, ServletException {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
            }
        };
    }

    /**
     * Chemins ouverts sans authentification en dehors de l'API : la santé (sondes Kubernetes, vérifications de
     * disponibilité) et les infos ; les métriques et la console H2 seulement si la configuration le demande.
     */
    private String[] operationalPublicPaths() {
        List<String> paths = new ArrayList<>(List.of("/actuator/health/**", "/actuator/info"));
        if (publicMetrics) {
            paths.add("/actuator/metrics/**");
            paths.add("/actuator/prometheus");
        }
        if (h2ConsoleEnabled) {
            paths.add("/h2-console/**");
        }
        return paths.toArray(String[]::new);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Autoriser les endpoints publics
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(operationalPublicPaths()).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/pharmacy/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pharmacies/**").permitAll()

                        // Tous les autres endpoints nécessitent une authentification
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // La console H2 s'affiche dans des cadres : on ne relâche la protection que si elle est activée.
                .headers(headers -> {
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable);
                    }
                });

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(allowedOrigins));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*")); // Allow all headers (Authorization, Content-Type,
                                                                 // etc.)
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}