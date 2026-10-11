package com.renault.fleet.auth.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.auth.dto.SetPasswordCheckDto;
import com.renault.fleet.auth.dto.SetPasswordLinkDto;
import com.renault.fleet.auth.entity.RevokeReason;
import com.renault.fleet.auth.entity.SetPasswordToken;
import com.renault.fleet.auth.repository.RefreshTokenRepository;
import com.renault.fleet.auth.repository.SetPasswordTokenRepository;
import com.renault.fleet.common.error.ApiException;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time set-password links (dealer invitations and admin resets). The raw token is 256 random bits shown once to
 * the admin; only its SHA-256 is stored. A link is single use, expires after {@code fleet.security.set-password.ttl},
 * and issuing a new one revokes the user's older links. Unknown token: 404; spent or expired: 410.
 */
@Service
public class SetPasswordService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SetPasswordTokenRepository tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;
    private final Duration ttl;
    private final String publicBaseUrl;

    public SetPasswordService(SetPasswordTokenRepository tokens, UserRepository users,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, AuditService audit, Clock clock,
            @Value("${fleet.security.set-password.ttl:72h}") Duration ttl,
            @Value("${fleet.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
        this.ttl = ttl;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public SetPasswordLinkDto issue(Long userId, AuthenticatedUser actor) {
        User user = users.findWithRoleById(userId).orElseThrow(() -> new NotFoundException("User " + userId + " not found."));
        if (user.getRole().getCode() == RoleCode.SUPERVISOR && actor.role() != RoleCode.SUPERVISOR) {
            throw new AccessDeniedException("Only a SUPERVISOR can manage SUPERVISOR accounts.");
        }
        if (!user.isActive()) {
            throw new ConflictException("User " + user.getUsername() + " is inactive; activate the account first.");
        }
        Instant now = clock.instant();
        int revoked = tokens.revokeOpenForUser(userId, now);
        String raw = newToken();
        Instant expiresAt = now.plus(ttl);
        tokens.saveAndFlush(new SetPasswordToken(users.getReferenceById(userId), RefreshTokenService.hash(raw), expiresAt,
                users.getReferenceById(actor.id())));
        audit.record(AuditEvent.of("SET_PASSWORD_LINK_ISSUED", "USER", String.valueOf(userId))
                .withActor(actor)
                .withReason("expires " + expiresAt + (revoked > 0 ? "; replaced " + revoked + " open link(s)" : "")));
        // Fragment, not query string: the token never reaches server or proxy logs, nor a Referer header.
        return new SetPasswordLinkDto(userId, user.getUsername(), publicBaseUrl + "/set-password#token=" + raw, expiresAt);
    }

    @Transactional(readOnly = true)
    public SetPasswordCheckDto check(String rawToken) {
        SetPasswordToken token = tokens.findByTokenHash(RefreshTokenService.hash(rawToken))
                .orElseThrow(SetPasswordService::unknown);
        requireUsable(token);
        return new SetPasswordCheckDto(token.getUser().getUsername(), token.getExpiresAt());
    }

    @Transactional
    public void setPassword(String rawToken, String password) {
        SetPasswordToken token = tokens.findByTokenHashForUpdate(RefreshTokenService.hash(rawToken))
                .orElseThrow(SetPasswordService::unknown);
        requireUsable(token);
        User user = token.getUser();
        if (password.toLowerCase(Locale.ROOT).contains(user.getUsername().toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("The password must not contain the username.");
        }
        Instant now = clock.instant();
        token.setUsedAt(now);
        tokens.saveAndFlush(token);
        tokens.revokeOpenForUser(user.getId(), now);
        user = users.findById(user.getId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(password));
        users.saveAndFlush(user);
        int signedOut = refreshTokens.revokeAllForUser(user.getId(), RevokeReason.PASSWORD_CHANGED, now);
        audit.record(AuditEvent.of("PASSWORD_SET_VIA_LINK", "USER", String.valueOf(user.getId()))
                .withActor(user.getId(), user.getUsername(), user.getRole().getCode().name())
                .withReason("set-password link " + token.getId() + " used; refresh tokens revoked: " + signedOut));
    }

    private void requireUsable(SetPasswordToken token) {
        if (!token.isUsable(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "Link No Longer Valid",
                    token.getUsedAt() != null ? "This set-password link has already been used."
                            : token.getRevokedAt() != null ? "This set-password link was replaced by a newer one."
                            : "This set-password link has expired. Ask your administrator for a new one.");
        }
        if (!token.getUser().isActive()) {
            throw new ApiException(HttpStatus.GONE, "Link No Longer Valid", "This account is inactive.");
        }
    }

    private static NotFoundException unknown() {
        return new NotFoundException("This set-password link is not valid.");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
