package com.renault.fleet.auth.mapper;

import com.renault.fleet.auth.dto.AuthUserDto;
import com.renault.fleet.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

@Component
public class AuthUserMapper {

    public AuthUserDto toDto(AuthenticatedUser user) {
        return new AuthUserDto(user.id(), user.username(), user.fullName(), user.email(), user.role(),
                user.permissions().stream().sorted().toList());
    }
}
