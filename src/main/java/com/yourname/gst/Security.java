package com.yourname.gst;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Security configuration for the GST Invoice Processor.
 * Contains:
 * 1. SecurityConfig - form login for admin endpoints
 * 2. TokenFilter - validates shop tokens for API endpoints
 * 3. ShopContext - ThreadLocal holder for current shop
 */
public class Security {

    /**
     * ThreadLocal holder for the current shop during request processing.
     */
    public static class ShopContext {
        private static final ThreadLocal<Models.Shop> currentShop = new ThreadLocal<>();

        public static void setShop(Models.Shop shop) {
            currentShop.set(shop);
        }

        public static Models.Shop getShop() {
            return currentShop.get();
        }

        public static void clear() {
            currentShop.remove();
        }
    }

    /**
     * OncePerRequestFilter that validates the X-Shop-Token header.
     * Reads the token, hashes it with SHA-256, finds an active token,
     * loads the associated shop, and sets it in ShopContext.
     */
    @Component
    public static class TokenFilter extends OncePerRequestFilter {

        private final Repos.AccessTokenRepository tokenRepository;

        public TokenFilter(Repos.AccessTokenRepository tokenRepository) {
            this.tokenRepository = tokenRepository;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            try {
                // Extract token from header
                String token = request.getHeader("X-Shop-Token");
                if (token == null || token.isBlank()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Shop-Token header");
                    return;
                }

                // Hash the token with SHA-256
                String tokenHash = hashToken(token);

                // Find active token
                Models.AccessToken accessToken = tokenRepository.findByTokenHash(tokenHash)
                        .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                        .orElse(null);

                if (accessToken == null) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                    return;
                }

                // Set shop in context
                ShopContext.setShop(accessToken.getShop());

                // Continue filter chain
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Token validation failed");
            } finally {
                // Always clear the context to prevent leaks
                ShopContext.clear();
            }
        }

        private String hashToken(String token) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(token.getBytes());
                return Base64.getEncoder().encodeToString(hashBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            }
        }
    }

    /**
     * Security configuration for HTTP endpoints.
     * - /admin/** and /admin-api/** require form login
     * - /api/** and static resources are permitted (handled by TokenFilter)
     * - CSRF token is stored in a cookie named "XSRF-TOKEN" for use by the admin panel
     */
    @Configuration
    public static class SecurityConfig {

        @Value("${admin.username}")
        private String adminUsername;

        @Value("${admin.password}")
        private String adminPassword;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf()
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .and()
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/admin/**", "/admin-api/**").authenticated()
                    .requestMatchers("/api/**", "/css/**", "/js/**", "/images/**", "/", "/index.html").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/admin/login")
                    .defaultSuccessUrl("/admin/", true)
                    .permitAll()
                )
                .logout(logout -> logout
                    .logoutSuccessUrl("/admin/login?logout")
                    .permitAll()
                );

            return http.build();
        }

        @Bean
        public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            UserDetails adminUser = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .roles("ADMIN")
                    .build();

            return username -> {
                if (adminUsername.equals(username)) {
                    return adminUser;
                }
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found");
            };
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}