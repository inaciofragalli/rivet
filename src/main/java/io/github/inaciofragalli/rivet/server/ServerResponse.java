package io.github.inaciofragalli.rivet.server;

import java.util.UUID;

public record ServerResponse(
        UUID id,
        String name,
        String host,
        Integer sshPort,
        String sshUsername
) {
}