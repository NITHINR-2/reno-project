package com.renault.fleet.common.seed;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.entity.BodyType;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.user.entity.Role;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only Dealer Portal data, after {@link DevBusinessSeeder}: catalogue text for the seeded models and one DEALER
 * user per seeded dealer ({@code dealer.<code>}, password from {@code DEV_DEALER_PASSWORD}). Idempotent.
 */
@Component
@Profile("dev")
@Order(3)
public class DevDealerPortalSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDealerPortalSeeder.class);
    static final String PASSWORD_KEY = "DEV_DEALER_PASSWORD";

    private final VehicleModelRepository models;
    private final DealerRepository dealers;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Environment environment;

    public DevDealerPortalSeeder(VehicleModelRepository models, DealerRepository dealers, UserRepository users,
            RoleRepository roles, PasswordEncoder passwordEncoder, AuditService audit, Environment environment) {
        this.models = models;
        this.dealers = dealers;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        catalogue("DUSTER", BodyType.SUV, 5,
                "Rugged five-seat SUV with high ground clearance; a fleet favourite for field teams.");
        catalogue("KIGER", BodyType.COMPACT_SUV, 5,
                "Compact SUV with a turbo-petrol option, sized for city fleets and pool cars.");
        catalogue("TRIBER", BodyType.MPV, 7,
                "Modular seven-seat MPV for staff transport and shuttles.");
        catalogue("KWID", BodyType.HATCHBACK, 5,
                "Economical entry hatchback for high-mileage sales and service fleets.");
        dealerUsers();
    }

    private void catalogue(String code, BodyType bodyType, int seats, String description) {
        models.findByCode(code).filter(m -> m.getDescription() == null).ifPresent((VehicleModel m) -> {
            m.setBodyType(bodyType);
            m.setSeatingCapacity(seats);
            m.setDescription(description);
        });
    }

    private void dealerUsers() {
        String password = environment.getProperty(PASSWORD_KEY);
        if (password == null || password.isBlank()) {
            log.warn("Dev seed: {} not set; skipping dealer users", PASSWORD_KEY);
            return;
        }
        Role role = roles.findByCode(RoleCode.DEALER)
                .orElseThrow(() -> new IllegalStateException("Role DEALER missing; check Flyway V13"));
        String hash = passwordEncoder.encode(password);
        for (Dealer dealer : dealers.findAll(Sort.by("dealerCode"))) {
            String username = "dealer." + dealer.getDealerCode().toLowerCase(Locale.ROOT);
            if (users.existsByDealerId(dealer.getId()) || users.existsByUsernameIgnoreCase(username)) {
                continue;
            }
            User user = new User(username, username + "@dealers.example", dealer.getName() + " (Portal)", hash, role);
            user.setDealer(dealer);
            users.save(user);
            audit.record(AuditEvent.of("USER_SEEDED", "USER", String.valueOf(user.getId()))
                    .withReason("dev profile seed (DEALER " + dealer.getDealerCode() + ")"));
            log.info("Dev seed: created dealer user '{}' for {}", username, dealer.getDealerCode());
        }
    }
}
