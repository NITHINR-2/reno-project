package com.renault.fleet.security;

import com.renault.fleet.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Rejects {@code /api/public/**} callers over {@link PublicRateLimiter}'s limit with 429 + Retry-After (ProblemDetail). */
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final PublicRateLimiter limiter;
    private final HandlerExceptionResolver resolver;

    public PublicRateLimitFilter(PublicRateLimiter limiter, HandlerExceptionResolver resolver) {
        this.limiter = limiter;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = limiter.acquire(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            resolver.resolveException(request, response, null, new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests", "Too many verification requests. Try again in " + retryAfter + " seconds."));
            return;
        }
        chain.doFilter(request, response);
    }
}
