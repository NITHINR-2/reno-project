package com.renault.fleet.auth.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.auth.entity.RefreshToken;
import com.renault.fleet.auth.entity.RevokeReason;
import com.renault.fleet.auth.repository.RefreshTokenRepository;
import com.renault.fleet.security.JwtProperties;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotating refresh tokens. Raw tokens are 256-bit random values; only their SHA-256 is stored (high entropy makes a
 * slow hash unnecessary and keeps lookup by hash possible). Reusing a rotated token revokes the whole family.
 *
 * <p>Rejections are returned as values rather than thrown, so the family revocation commits before the caller
 * answers 401.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository, AuditService auditService,
            JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new token family (at login) and returns the raw token for the cookie. */
    @Transactional
    public String issueNewFamily(Long userId, ClientInfo client) {
        return create(userRepository.getReferenceById(userId), UUID.randomUUID(), client).rawToken();
    }

    @Transactional
    public RotationResult rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return new RotationResult.Rejected("UNKNOWN");
        }
        RefreshToken token = found.get();
        if (token.getRotatedAt() != null) {
            // Read what the audit row needs before the bulk update clears the persistence context.
            User owner = token.getUser();
            Long ownerId = owner.getId();
            String ownerUsername = owner.getUsername();
            UUID familyId = token.getFamilyId();
            int revoked = repository.revokeFamily(familyId, RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}", ownerId, revoked, familyId);
            auditService.record(AuditEvent.of("REFRESH_TOKEN_REUSE_DETECTED", "REFRESH_TOKEN_FAMILY", familyId.toString())
                    .withActor(ownerId, ownerUsername, null)
                    .withReason("Rotated refresh token presented again; family revoked")
                    .withIp(client.ipAddress()));
            return new RotationResult.Rejected("REUSE_DETECTED");
        }
        if (token.getRevokedAt() != null) {
            return new RotationResult.Rejected("REVOKED");
        }
        if (token.isExpired(now)) {
            return new RotationResult.Rejected("EXPIRED");
        }
        User user = token.getUser();
        if (!user.isActive()) {
            repository.revokeFamily(token.getFamilyId(), RevokeReason.USER_DEACTIVATED, now);
            return new RotationResult.Rejected("USER_INACTIVE");
        }
        Created successor = create(user, token.getFamilyId(), client);
        token.markRotated(now, successor.entity());
        return new RotationResult.Rotated(successor.rawToken(), user.getId());
    }

    /** Revokes the family the presented token belongs to; returns its owner's id when the token was known. */
    @Transactional
    public Optional<Long> revokeFamily(String rawToken, RevokeReason reason) {
        return repository.findByTokenHashForUpdate(hash(rawToken)).map(token -> {
            Long userId = token.getUser().getId();
            repository.revokeFamily(token.getFamilyId(), reason, clock.instant());
            return userId;
        });
    }

    private Created create(User user, UUID familyId, ClientInfo client) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        RefreshToken entity = repository.save(
                new RefreshToken(user, hash(raw), familyId, expiresAt, client.userAgent(), client.ipAddress()));
        return new Created(raw, entity);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record Created(String rawToken, RefreshToken entity) {
    }

    public sealed interface RotationResult {

        record Rotated(String rawToken, Long userId) implements RotationResult {
        }

        record Rejected(String reason) implements RotationResult {
        }
    }
}
