package com.bookstore.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Configuration
public class SecurityConfig {

    /** Header the frontend sends with every API call; see {@link ApiCsrfFilter}. */
    public static final String CSRF_HEADER = "X-Requested-With";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ApiCsrfFilter apiCsrfFilter() {
        return new ApiCsrfFilter();
    }

    /**
     * CSRF protection for the session-cookie API. State-changing /api requests must carry a custom
     * header. A page on another site can't add one without a CORS preflight, and this app grants no
     * cross-origin access. Together with the SameSite=Lax session cookie, that means forged form
     * posts and cross-site fetches are rejected.
     */
    public static class ApiCsrfFilter extends OncePerRequestFilter {

        private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/") || SAFE_METHODS.contains(request.getMethod());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader(CSRF_HEADER);
            if (header == null || header.isBlank()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Request blocked: missing " + CSRF_HEADER + " header.\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
