package io.github.inaciofragalli.rivet.image;

import java.util.List;

public record ImageMetadata(
        String image,
        String tag,
        String architecture,
        String os,
        List<Integer> exposedPorts
) {}
