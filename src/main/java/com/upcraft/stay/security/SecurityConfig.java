package com.upcraft.stay.security;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.upcraft.stay.common.config.AppProperties;

/**
 * Spring Security configuration for legacy {@code /api/**} and versioned
 * {@code /api/v1/**} endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Public Auth & Public Info
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/google",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-otp",
                                "/api/auth/reset-password",
                                "/api/public/**",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/reset-password",
                                "/api/v1/invitations/verify",
                                "/api/v1/invitations/accept")
                        .permitAll()

                        // Super Admin endpoints
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("SUPER_ADMIN")

                        // Versioned SaaS v1 endpoints (granular guards enforced via @PreAuthorize +
                        // AccessGuardService)
                        .requestMatchers("/api/v1/**").authenticated()

                        // Legacy Owner-only endpoints
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/stats").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/members", "/api/members/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/members", "/api/members/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/members/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/members/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/rooms").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/rooms").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/menu").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/menu/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/menu/**").hasRole("PG_OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/complaints").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/complaints/**").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/queries").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/queries/**").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/announcements").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/announcements/**").hasAnyRole("PG_OWNER", "MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/announcements/**").hasRole("PG_OWNER")

                        // Legacy Member-only endpoints
                        .requestMatchers(HttpMethod.GET, "/api/complaints/mine").hasRole("PG_MEMBER")
                        .requestMatchers(HttpMethod.POST, "/api/complaints").hasRole("PG_MEMBER")
                        .requestMatchers(HttpMethod.GET, "/api/queries/mine").hasRole("PG_MEMBER")
                        .requestMatchers(HttpMethod.POST, "/api/queries").hasRole("PG_MEMBER")
                        .requestMatchers(HttpMethod.GET, "/api/rooms/mine").hasRole("PG_MEMBER")

                        // Any authenticated user
                        .requestMatchers(HttpMethod.GET, "/api/announcements").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/menu/weekly").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/queries/*/reply").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/members/profile", "/api/members/profile/password")
                        .authenticated()
                        .requestMatchers("/api/notifications", "/api/notifications/**").authenticated()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        if (CollectionUtils.isNotEmpty(appProperties.getCors().getAllowedOrigins())) {
            origins.addAll(appProperties.getCors().getAllowedOrigins());
        }
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
