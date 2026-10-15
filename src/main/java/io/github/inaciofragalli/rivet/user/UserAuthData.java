package io.github.inaciofragalli.rivet.user;

import java.util.UUID;

public record UserAuthData(
        UUID id,
        String email,
        String passwordHash
) {
}
