package com.renault.fleet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * CSRF guard for the two cookie-authenticated endpoints. A custom header can't be set cross-site without a CORS
 * preflight (which we never allow), so its presence proves the call came from our own origin. SameSite=Strict on
 * the cookie is the first line of defence; this is the second.
 *
 * <p>Instantiated by {@code SecurityConfig} (not a bean) so it only runs inside the security filter chain.
 */
public class XRequestedWithFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    static final Set<String> PROTECTED_PATHS = Set.of("/api/auth/refresh", "/api/auth/logout");

    private final HandlerExceptionResolver resolver;

    public XRequestedWithFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PROTECTED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            resolver.resolveException(request, response, null, new MissingCsrfHeaderException());
            return;
        }
        chain.doFilter(request, response);
    }
}
