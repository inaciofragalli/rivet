package io.github.inaciofragalli.rivet.application.internal;

import jakarta.validation.constraints.*;
import java.util.UUID;
import io.github.inaciofragalli.rivet.image.DockerImageReference;

public record CreateApplicationRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")
        String name,

        String image,

        UUID imageArtifactId,

        @NotNull
        @Min(1)
        @Max(65535)
        Integer containerPort
) {
        @AssertTrue(message = "Provide either image or imageArtifactId")
        public boolean isImageSourceValid() {
                boolean hasImage = image != null && !image.isBlank();
                boolean hasArtifact = imageArtifactId != null;

                return hasImage ^ hasArtifact;
        }

        @AssertTrue(message = "Invalid Docker image reference")
        public boolean isImageReferenceValid() {
                return image == null
                        || image.isBlank()
                        || DockerImageReference.isValid(image);
        }
}