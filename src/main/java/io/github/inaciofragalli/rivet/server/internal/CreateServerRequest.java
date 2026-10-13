package io.github.inaciofragalli.rivet.server.internal;

import jakarta.validation.constraints.*;

public record CreateServerRequest(
        @NotBlank
        @Size(max = 30)
        String name,

        @NotBlank
        @Size(max = 255)
        String host,

        @NotNull
        @Min(1)
        @Max(65535)
        Integer sshPort,

        @NotBlank
        @Size(max = 30)
        String sshUsername
) {
}
