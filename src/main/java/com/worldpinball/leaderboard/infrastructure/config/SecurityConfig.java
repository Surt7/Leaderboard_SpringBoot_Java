package com.worldpinball.leaderboard.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public OpenAPI and Swagger documentation
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // Public health and observability probes
                        .requestMatchers(
                                "/alive",
                                "/health",
                                "/actuator/**"
                        ).permitAll()
                        // Public catalogue and leaderboard endpoints
                        .requestMatchers(
                                "/api/v1/gamemodes/**",
                                "/api/v1/game-modes/**",
                                "/api/v1/iplicenses/**",
                                "/api/v1/ip-licenses/**",
                                "/api/v1/leaderboards/**"
                        ).permitAll()
                        // Machine public enrollment
                        .requestMatchers(
                                "/api/v1/machines/enroll"
                        ).permitAll()
                        // Local developer test endpoints
                        .requestMatchers(
                                "/api/v1/test/**"
                        ).permitAll()
                        // Admin-only endpoints protected by Keycloak Admin role
                        .requestMatchers(
                                "/api/v1/admin/**"
                        ).hasAnyRole("Admin", "ADMIN")
                        // Allow other endpoints or authenticated where appropriate
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("https://*.worldpinball.local", "http://*.worldpinball.local", "https://worldpinball.local", "http://worldpinball.local", "http://localhost:*", "https://localhost:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    /**
     * Extracts Keycloak realm roles mapped in "roles" or "realm_access.roles"
     * and maps them as Spring Security GrantedAuthority (ROLE_Admin, ROLE_ADMIN, Admin).
     */
    static class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // 1. Check direct "roles" claim (custom OIDC realm-roles mapper)
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> col) {
                for (Object r : col) {
                    if (r != null) {
                        String role = r.toString();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
            }

            // 2. Check "realm_access.roles" (Keycloak standard claim)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rRoles = realmAccess.get("roles");
                if (rRoles instanceof Collection<?> col) {
                    for (Object r : col) {
                        if (r != null) {
                            String role = r.toString();
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                            authorities.add(new SimpleGrantedAuthority(role));
                        }
                    }
                }
            }

            return authorities;
        }
    }
}
