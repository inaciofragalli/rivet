package io.github.inaciofragalli.rivet.image;

import java.util.List;
import java.util.UUID;

public record ImageArtifactResponse(
        UUID id,
        String image,
        String tag,
        String architecture,
        String os,
        List<Integer> exposedPorts,
        long archiveSizeBytes,
        String sha256
) {}