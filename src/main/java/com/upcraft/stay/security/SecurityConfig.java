package com.upcraft.stay.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
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

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers("/api/auth/login", "/api/auth/google", "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/verify-otp", "/api/auth/reset-password", "/api/public/**").permitAll()

                // Super Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                // Owner-only management endpoints
                .requestMatchers(HttpMethod.GET,    "/api/dashboard/stats").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.GET,    "/api/members", "/api/members/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.POST,   "/api/members", "/api/members/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PATCH,  "/api/members/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/members/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.GET,    "/api/rooms").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.POST,   "/api/rooms").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PUT,    "/api/rooms/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.POST,   "/api/menu").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PUT,    "/api/menu/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/menu/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.GET,    "/api/complaints").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PATCH,  "/api/complaints/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.GET,    "/api/queries").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PATCH,  "/api/queries/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.POST,   "/api/announcements").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.PUT,    "/api/announcements/**").hasRole("PG_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/announcements/**").hasRole("PG_OWNER")

                // Member-only endpoints
                .requestMatchers(HttpMethod.GET,    "/api/complaints/mine").hasRole("PG_MEMBER")
                .requestMatchers(HttpMethod.POST,   "/api/complaints").hasRole("PG_MEMBER")
                .requestMatchers(HttpMethod.GET,    "/api/queries/mine").hasRole("PG_MEMBER")
                .requestMatchers(HttpMethod.POST,   "/api/queries").hasRole("PG_MEMBER")
                .requestMatchers(HttpMethod.GET,    "/api/rooms/mine").hasRole("PG_MEMBER")

                // Any authenticated user
                .requestMatchers(HttpMethod.GET,    "/api/announcements").authenticated()
                .requestMatchers(HttpMethod.GET,    "/api/menu/weekly").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/queries/*/reply").authenticated()
                .requestMatchers(HttpMethod.PUT,    "/api/members/profile", "/api/members/profile/password").authenticated()
                .requestMatchers("/api/notifications", "/api/notifications/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
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
