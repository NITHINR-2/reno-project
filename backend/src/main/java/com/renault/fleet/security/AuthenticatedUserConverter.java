package com.renault.fleet.security;

import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a verified access token into an authentication whose role and permissions come from the database,
 * not from token claims. A deactivated user or a changed role takes effect on the very next request.
 */
@Component
public class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public AuthenticatedUserConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        User user = userRepository.findWithAuthoritiesById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Access token is no longer valid"));
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException ex) {
            throw new BadCredentialsException("Access token has an invalid subject");
        }
    }
}
