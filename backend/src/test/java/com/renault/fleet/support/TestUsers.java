package com.renault.fleet.support;

import com.renault.fleet.user.entity.Role;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.RoleRepository;
import com.renault.fleet.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates uniquely named users (the Testcontainers database is shared by every test in a context). */
public final class TestUsers {

    public static final String PASSWORD = "Test-Passw0rd!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final String passwordHash;

    public TestUsers(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    public User create(RoleCode roleCode) {
        return create(roleCode, true);
    }

    public User create(RoleCode roleCode, boolean active) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        String username = roleCode.name().toLowerCase().replace('_', '.') + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(username, username + "@test.local", "Test " + roleCode, passwordHash, role);
        user.setActive(active);
        return userRepository.save(user);
    }
}
