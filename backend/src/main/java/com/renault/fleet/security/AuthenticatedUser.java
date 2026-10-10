package com.renault.fleet.security;

import com.renault.fleet.user.entity.Permission;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The server-side principal, rebuilt from the database on every request. Authorities are
 * {@code ROLE_<role>} plus every permission code of that role.
 */
public record AuthenticatedUser(Long id, String username, String fullName, String email, RoleCode role,
        Set<String> permissions) implements AuthenticatedPrincipal {

    /** Requires {@code user.role.permissions} to be initialised. */
    public static AuthenticatedUser from(User user) {
        Set<String> permissions = user.getRole().getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRole().getCode(), Set.copyOf(permissions));
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissions.stream().sorted().map(SimpleGrantedAuthority::new).forEach(authorities::add);
        return authorities;
    }

    @Override
    public String getName() {
        return username;
    }
}
