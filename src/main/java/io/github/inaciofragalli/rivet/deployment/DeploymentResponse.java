package io.github.inaciofragalli.rivet.deployment;

import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID applicationId,
        UUID serverId,
        UUID imageArtifactId,
        String imageReference,
        Integer containerPort,
        DeploymentStatus status,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt,
        String containerId,
        String errorCode,
        String errorMessage
) {
}
