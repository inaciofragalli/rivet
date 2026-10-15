package io.github.inaciofragalli.rivet.user;

import java.util.Optional;

public interface UserLookup {
    Optional<UserAuthData> findByEmail(String email);
}
