// src/main/java/com/thomas/order_management/config/SecurityConfig.java
package com.thomas.order_management.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * Zustandslose Absicherung per JWT (HS256). Lesen dürfen alle angemeldeten Benutzer,
 * Schreibzugriffe sind auf die fachlich zuständigen Rollen beschränkt.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String SALES = "SALES";
    private static final String WAREHOUSE = "WAREHOUSE";
    private static final String BILLING = "BILLING";
    private static final String CONTROLLING = "CONTROLLING";
    private static final String ADMIN = "ADMIN";

    @Value("${app.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${app.cors.allowed-origins:http://localhost:4200,https://order-management-front.netlify.app,https://*--order-management-front.netlify.app}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/demo-users").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/images/**", "/images/**").permitAll()

                .requestMatchers("/api/users/**").hasRole(ADMIN)

                // Simulation ist eine reine Prüfung ohne Buchung
                .requestMatchers(HttpMethod.POST, "/api/orders/simulate").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/order-requests/**").hasAnyRole(SALES, ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/orders/*/release-credit").hasAnyRole(BILLING, ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/orders/backlog/process").hasAnyRole(SALES, WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/orders/**").hasAnyRole(SALES, ADMIN)
                .requestMatchers(HttpMethod.PATCH, "/api/orders/**").hasAnyRole(SALES, ADMIN)

                .requestMatchers(HttpMethod.POST, "/api/deliveries/**").hasAnyRole(WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/invoices/**").hasAnyRole(BILLING, ADMIN)

                .requestMatchers(HttpMethod.POST, "/api/inventory/**").hasAnyRole(WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasAnyRole(WAREHOUSE, ADMIN)

                .requestMatchers(HttpMethod.POST, "/api/customers/**").hasAnyRole(SALES, BILLING, ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/customers/**").hasAnyRole(SALES, BILLING, ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasRole(ADMIN)

                .requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyRole(SALES, WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyRole(SALES, WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAnyRole(WAREHOUSE, ADMIN)

                // KI-Läufe und Wartung verursachen Kosten bei OpenAI
                .requestMatchers(HttpMethod.POST, "/api/reviews/maintenance/**", "/api/reviews/insights/**")
                        .hasAnyRole(CONTROLLING, ADMIN)
                .requestMatchers(HttpMethod.POST, "/reviews/**", "/api/reviews/**").hasAnyRole(SALES, ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/reviews/**").hasAnyRole(SALES, ADMIN)

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey() {
        byte[] key;
        if (jwtSecret == null || jwtSecret.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            log.warn("app.security.jwt-secret ist nicht gesetzt – es wird ein zufälliger Schlüssel verwendet; "
                    + "Anmeldungen werden bei jedem Neustart ungültig. Für den Betrieb JWT_SECRET setzen.");
        } else {
            key = jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new IllegalStateException("app.security.jwt-secret muss mindestens 32 Zeichen lang sein");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location", "Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
