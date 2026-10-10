package io.github.inaciofragalli.rivet.application;

import io.github.inaciofragalli.rivet.application.internal.Application;

import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String image,
        Integer containerPort
) {
}
