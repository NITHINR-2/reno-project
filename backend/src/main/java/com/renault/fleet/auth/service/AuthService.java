package com.renault.fleet.auth.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.auth.dto.AuthUserDto;
import com.renault.fleet.auth.entity.RevokeReason;
import com.renault.fleet.auth.mapper.AuthUserMapper;
import com.renault.fleet.auth.service.AccessTokenService.IssuedAccessToken;
import com.renault.fleet.auth.service.RefreshTokenService.RotationResult;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login, refresh, logout and "me". Not transactional as a whole: each step commits on its own so that audit rows
 * for failures (and family revocation on reuse) survive the 401 that follows.
 */
@Service
public class AuthService {

    private static final String ENTITY_USER = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final AuthUserMapper mapper;
    private final Clock clock;
    /** Compared against when the user doesn't exist, so response time doesn't reveal which usernames exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService, AuditService auditService, AuthUserMapper mapper, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthSession login(String username, String password, ClientInfo client) {
        Optional<User> found = userRepository.findWithAuthoritiesByUsername(username.trim());
        boolean passwordMatches = passwordEncoder.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            auditService.record(AuditEvent.of("LOGIN_FAILED", ENTITY_USER, found.map(u -> String.valueOf(u.getId())).orElse(null))
                    .withActor(null, username, null)
                    .withReason(found.isEmpty() ? "unknown user" : !passwordMatches ? "bad password" : "inactive user")
                    .withIp(client.ipAddress()));
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        userRepository.updateLastLoginAt(user.getId(), clock.instant());
        String refreshToken = refreshTokenService.issueNewFamily(user.getId(), client);
        audit("LOGIN_SUCCESS", user, client);
        return session(user, refreshToken);
    }

    public AuthSession refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RotationResult result = refreshTokenService.rotate(rawRefreshToken, client);
        if (!(result instanceof RotationResult.Rotated rotated)) {
            throw new InvalidRefreshTokenException();
        }
        User user = userRepository.findWithAuthoritiesById(rotated.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return session(user, rotated.rawToken());
    }

    /** Always succeeds: an unknown or missing cookie simply means there is nothing to revoke. */
    public void logout(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenService.revokeFamily(rawRefreshToken, RevokeReason.LOGOUT)
                .flatMap(userRepository::findWithAuthoritiesById)
                .ifPresent(user -> audit("LOGOUT", user, client));
    }

    public AuthUserDto me(AuthenticatedUser principal) {
        return mapper.toDto(principal);
    }

    private AuthSession session(User user, String refreshToken) {
        IssuedAccessToken access = accessTokenService.issue(user);
        return new AuthSession(access.value(), access.expiresInSeconds(), refreshToken,
                mapper.toDto(AuthenticatedUser.from(user)));
    }

    private void audit(String action, User user, ClientInfo client) {
        auditService.record(AuditEvent.of(action, ENTITY_USER, String.valueOf(user.getId()))
                .withActor(user.getId(), user.getUsername(), user.getRole().getCode().name())
                .withIp(client.ipAddress()));
    }
}
