package io.github.inaciofragalli.rivet.application.internal;

import jakarta.validation.constraints.*;

public record CreateApplicationRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")
        String name,

        @NotBlank
        String image,

        @NotNull
        @Min(1)
        @Max(65535)
        Integer containerPort
) {
}
