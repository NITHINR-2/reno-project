package com.renault.fleet.user.seed;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.user.entity.Role;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev profile only: one user per role, with passwords read from {@code DEV_*_PASSWORD} (environment or the
 * git-ignored backend/.env). Existing users are left untouched; a missing password skips that user.
 */
@Component
@Profile("dev")
@Order(1)
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private record SeedUser(String username, String fullName, RoleCode role, String passwordKey) {
    }

    private static final List<SeedUser> USERS = List.of(
            new SeedUser("admin", "Asha Admin", RoleCode.ADMIN, "DEV_ADMIN_PASSWORD"),
            new SeedUser("supervisor", "Suresh Supervisor", RoleCode.SUPERVISOR, "DEV_SUPERVISOR_PASSWORD"),
            new SeedUser("marketing.manager", "Meera Marketing Manager", RoleCode.MARKETING_MANAGER, "DEV_MARKETING_MANAGER_PASSWORD"),
            new SeedUser("marketing.head", "Mohan Marketing Head", RoleCode.MARKETING_HEAD, "DEV_MARKETING_HEAD_PASSWORD"),
            new SeedUser("finance", "Farah Finance", RoleCode.FINANCE_TEAM, "DEV_FINANCE_PASSWORD"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Environment environment;

    public DevUserSeeder(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            AuditService auditService, Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedUser seed : USERS) {
            if (userRepository.existsByUsernameIgnoreCase(seed.username())) {
                continue;
            }
            String password = environment.getProperty(seed.passwordKey());
            if (password == null || password.isBlank()) {
                log.warn("Dev seed: {} not set; skipping user '{}'", seed.passwordKey(), seed.username());
                continue;
            }
            Role role = roleRepository.findByCode(seed.role())
                    .orElseThrow(() -> new IllegalStateException("Role " + seed.role() + " missing; check Flyway V6"));
            User user = userRepository.save(new User(seed.username(), seed.username() + "@fleet.local", seed.fullName(),
                    passwordEncoder.encode(password), role));
            auditService.record(AuditEvent.of("USER_SEEDED", "USER", String.valueOf(user.getId()))
                    .withReason("dev profile seed (" + seed.role() + ")"));
            log.info("Dev seed: created user '{}' ({})", seed.username(), seed.role());
        }
    }
}
