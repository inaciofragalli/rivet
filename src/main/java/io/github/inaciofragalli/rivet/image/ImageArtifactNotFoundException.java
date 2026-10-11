package io.github.inaciofragalli.rivet.image;

import java.util.UUID;

public class ImageArtifactNotFoundException extends RuntimeException {
    public ImageArtifactNotFoundException(UUID id) {
        super("Image artifact not found: " + id);
    }
}