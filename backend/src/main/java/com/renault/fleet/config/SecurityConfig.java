package com.renault.fleet.config;

import com.renault.fleet.security.AuthenticatedUserConverter;
import com.renault.fleet.security.ProblemDetailSecurityHandler;
import com.renault.fleet.security.PublicRateLimitFilter;
import com.renault.fleet.security.PublicRateLimiter;
import com.renault.fleet.security.XRequestedWithFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless bearer-token API. Access tokens are JWTs verified by the resource server; the principal's role and
 * permissions are reloaded from the database ({@link AuthenticatedUserConverter}). Business endpoints are further
 * protected with method-level {@code @PreAuthorize} checks.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticatedUserConverter converter,
            ProblemDetailSecurityHandler problemHandler, PublicRateLimiter publicRateLimiter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        http
                // Bearer tokens aren't sent automatically by browsers; the refresh cookie has its own guard
                // (SameSite=Strict + X-Requested-With), so the session-oriented CSRF token isn't needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .addFilterBefore(new XRequestedWithFilter(resolver), BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new PublicRateLimitFilter(publicRateLimiter, resolver), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/health", "/api/public/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
