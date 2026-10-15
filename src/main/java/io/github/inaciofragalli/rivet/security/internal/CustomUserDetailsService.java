package io.github.inaciofragalli.rivet.security.internal;

import io.github.inaciofragalli.rivet.user.UserAuthData;
import io.github.inaciofragalli.rivet.user.UserLookup;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserLookup userLookup;

    @Override
    public AuthenticatedUser loadUserByUsername(@Nonnull String email) throws UsernameNotFoundException {
        UserAuthData user = userLookup.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AuthenticatedUser(user);
    }
}
