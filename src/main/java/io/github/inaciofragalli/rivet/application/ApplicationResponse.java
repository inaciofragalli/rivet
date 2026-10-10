package io.github.inaciofragalli.rivet.application;

import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String image,
        Integer containerPort
) {
}
