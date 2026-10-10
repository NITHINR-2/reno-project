package com.renault.fleet.auth.controller;

import com.renault.fleet.auth.dto.AuthUserDto;
import com.renault.fleet.auth.dto.LoginRequest;
import com.renault.fleet.auth.dto.TokenResponse;
import com.renault.fleet.auth.service.AuthService;
import com.renault.fleet.auth.service.AuthSession;
import com.renault.fleet.auth.service.ClientInfo;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookies;

    public AuthController(AuthService authService, RefreshCookieFactory cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in", description = "Returns an access token and sets the HttpOnly refresh cookie.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(authService.login(request.username(), request.password(), ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate the refresh cookie and get a new access token",
            description = "Requires the X-Requested-With header. Reusing a rotated token revokes the whole session family.")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest http) {
        return withSession(authService.refresh(refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Sign out", description = "Requires the X-Requested-With header. Revokes the session and clears the cookie.")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest http) {
        authService.logout(refreshToken, ClientInfo.from(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user, role and permissions")
    public AuthUserDto me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(principal);
    }

    private ResponseEntity<TokenResponse> withSession(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(TokenResponse.bearer(session.accessToken(), session.expiresInSeconds(), session.user()));
    }
}
