package io.github.inaciofragalli.rivet.application;

import io.github.inaciofragalli.rivet.application.internal.Application;

import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String image,
        Integer containerPort
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getName(),
                application.getImage(),
                application.getContainerPort()
        );
    }
}
