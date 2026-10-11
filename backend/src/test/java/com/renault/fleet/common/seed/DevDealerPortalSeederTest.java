package com.renault.fleet.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.support.TestFleetData;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** The dev seed gives every dealer one DEALER login (password from the environment) and is idempotent. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DevDealerPortalSeederTest {

    @Autowired private VehicleModelRepository models;
    @Autowired private com.renault.fleet.catalog.repository.VehicleVariantRepository variants;
    @Autowired private com.renault.fleet.catalog.repository.VehicleColourRepository colours;
    @Autowired private DealerRepository dealers;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditService audit;
    @Autowired private TestFleetData data;
    @Autowired private TransactionTemplate tx;

    @Test
    void createsOneDealerUserPerDealerOnce() {
        Dealer dealer = data.world().dealer();
        MockEnvironment env = new MockEnvironment().withProperty(DevDealerPortalSeeder.PASSWORD_KEY, "Dev-Dealer-Pass-1");
        DevDealerPortalSeeder seeder = new DevDealerPortalSeeder(models, variants, colours, dealers, users, roles, passwordEncoder, audit, env);

        tx.executeWithoutResult(s -> seeder.run(new DefaultApplicationArguments()));
        long after = users.count();
        tx.executeWithoutResult(s -> seeder.run(new DefaultApplicationArguments()));

        assertThat(users.count()).isEqualTo(after);
        User seeded = users.findWithAuthoritiesByUsername("dealer." + dealer.getDealerCode().toLowerCase()).orElseThrow();
        assertThat(seeded.getRole().getCode()).isEqualTo(RoleCode.DEALER);
        assertThat(passwordEncoder.matches("Dev-Dealer-Pass-1", seeded.getPasswordHash())).isTrue();
        assertThat(dealers.findAll()).allSatisfy(d -> assertThat(users.existsByDealerId(d.getId())).isTrue());
    }

    @Test
    void withoutThePasswordNoDealerUsersAreCreated() {
        long before = users.count();
        data.world();
        new DevDealerPortalSeeder(models, variants, colours, dealers, users, roles, passwordEncoder, audit, new MockEnvironment())
                .run(new DefaultApplicationArguments());
        assertThat(users.count()).isEqualTo(before);
    }
}
