package com.renault.fleet.user.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.auth.entity.RevokeReason;
import com.renault.fleet.auth.repository.RefreshTokenRepository;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.dto.CreateUserRequest;
import com.renault.fleet.user.dto.UpdateUserRequest;
import com.renault.fleet.user.dto.UserDto;
import com.renault.fleet.user.dto.UserFilter;
import com.renault.fleet.user.entity.Role;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admin Console user management. Only a SUPERVISOR may create, change, (de)activate or grant the SUPERVISOR role
 * (otherwise an ADMIN could escalate into operational access); nobody may change their own role or deactivate
 * themselves. Deactivation revokes every refresh token; access tokens stop working on the next request.
 */
@Service
public class UserAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final DealerRepository dealers;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public UserAdminService(UserRepository users, DealerRepository dealers, RoleRepository roles,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, AuditService audit,
            JsonMapper jsonMapper, Clock clock) {
        this.users = users;
        this.dealers = dealers;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(UserFilter f, Pageable pageable) {
        List<Specification<User>> specs = new ArrayList<>();
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("username")), like, '\\'),
                    cb.like(cb.lower(root.get("fullName")), like, '\\'), cb.like(cb.lower(root.get("email")), like, '\\')));
        }
        if (f.role() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("role").get("code"), f.role()));
        }
        if (f.active() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("active"), f.active()));
        }
        return PageResponse.from(users.findAll(Specification.allOf(specs), pageable), UserAdminService::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest body, AuthenticatedUser actor) {
        guardSupervisorRole(body.role(), actor);
        if (users.existsByUsernameIgnoreCase(body.username())) {
            throw new ConflictException("Username " + body.username() + " is already taken.");
        }
        if (users.existsByEmailIgnoreCase(body.email())) {
            throw new ConflictException("Email " + body.email() + " is already in use.");
        }
        Dealer dealer = dealerFor(body);
        if (body.password() == null && dealer == null) {
            throw new BadRequestException("A password is required for HQ users.");
        }
        if (body.password() != null && body.password().toLowerCase(Locale.ROOT).contains(body.username())) {
            throw new BadRequestException("The password must not contain the username.");
        }
        // Without a password the account can't sign in until the dealer uses a set-password link.
        String password = body.password() != null ? body.password() : unusablePassword();
        User newUser = new User(body.username(), body.email().strip(), body.fullName().strip(),
                passwordEncoder.encode(password), role(body.role()));
        newUser.setDealer(dealer);
        User user = users.saveAndFlush(newUser);
        UserDto dto = toDto(user);
        audit.record(AuditEvent.of("CREATE", "USER", String.valueOf(user.getId()))
                .withActor(actor).withChange(null, json(dto)));
        return dto;
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest body, AuthenticatedUser actor) {
        User user = find(id);
        Versions.check(user, body.version());
        guardSupervisorRole(user.getRole().getCode(), actor);
        String before = json(toDto(user));
        if (body.role() != null && body.role() != user.getRole().getCode()) {
            if (body.role() == RoleCode.DEALER || user.getRole().getCode() == RoleCode.DEALER) {
                throw new ConflictException("Dealer accounts can't change role; create a new account instead.");
            }
            if (user.getId().equals(actor.id())) {
                throw new ConflictException("You cannot change your own role.");
            }
            guardSupervisorRole(body.role(), actor);
            user.setRole(role(body.role()));
        }
        if (body.email() != null && !body.email().equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmailIgnoreCaseAndIdNot(body.email(), id)) {
                throw new ConflictException("Email " + body.email() + " is already in use.");
            }
            user.setEmail(body.email().strip());
        }
        if (body.fullName() != null) {
            user.setFullName(body.fullName().strip());
        }
        UserDto dto = toDto(users.saveAndFlush(user));
        audit.record(AuditEvent.of("UPDATE", "USER", String.valueOf(id)).withActor(actor).withChange(before, json(dto)));
        return dto;
    }

    /** Idempotent: setting the current value changes nothing and writes no audit row. */
    @Transactional
    public UserDto setActive(Long id, boolean active, AuthenticatedUser actor) {
        User user = find(id);
        if (user.getId().equals(actor.id())) {
            throw new ConflictException("You cannot change your own activation.");
        }
        guardSupervisorRole(user.getRole().getCode(), actor);
        if (user.isActive() == active) {
            return toDto(user);
        }
        String before = json(toDto(user));
        user.setActive(active);
        users.saveAndFlush(user);
        int revoked = active ? 0 : refreshTokens.revokeAllForUser(id, RevokeReason.USER_DEACTIVATED, Instant.now(clock));
        UserDto dto = toDto(user);
        audit.record(AuditEvent.of(active ? "ACTIVATE" : "DEACTIVATE", "USER", String.valueOf(id))
                .withActor(actor).withChange(before, json(dto))
                .withReason(active ? null : "refresh tokens revoked: " + revoked));
        return dto;
    }

    private static void guardSupervisorRole(RoleCode role, AuthenticatedUser actor) {
        if (role == RoleCode.SUPERVISOR && actor.role() != RoleCode.SUPERVISOR) {
            throw new AccessDeniedException("Only a SUPERVISOR can manage SUPERVISOR accounts.");
        }
    }

    /** DEALER accounts must name an existing dealership; HQ accounts must not. */
    private Dealer dealerFor(CreateUserRequest body) {
        if (body.role() != RoleCode.DEALER) {
            if (body.dealerId() != null) {
                throw new BadRequestException("Only DEALER accounts can be linked to a dealer.");
            }
            return null;
        }
        if (body.dealerId() == null) {
            throw new BadRequestException("A DEALER account needs a dealerId.");
        }
        return dealers.findById(body.dealerId())
                .orElseThrow(() -> new NotFoundException("Dealer " + body.dealerId() + " not found."));
    }

    private static String unusablePassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private User find(Long id) {
        return users.findWithRoleById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found."));
    }

    private Role role(RoleCode code) {
        return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("Role " + code + " missing"));
    }

    private String json(UserDto dto) {
        return jsonMapper.writeValueAsString(dto);
    }

    static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getRole().getCode(),
                u.isActive(), u.getLastLoginAt(), u.getVersion(), u.getCreatedAt(), u.getUpdatedAt(),
                u.getDealer() == null ? null : u.getDealer().getId());
    }
}
