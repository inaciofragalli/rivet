package io.github.inaciofragalli.rivet.image;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DockerImageReferenceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "nginx",
            "nginx:alpine",
            "ghcr.io/usuario/api:v1",
            "localhost:5000/api:latest",
            "registry.example.com:5000/my-team/app:1.0",
            "usuario/minha_api:v2",
            "[::1]:5000/app:v1"
    })
    void shouldAcceptValidReferences(String reference) {
        assertTrue(DockerImageReference.isValid(reference));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "nginx:",
            "nginx:bad tag",
            "nginx/UPPER:v1",
            "ghcr.io//app:v1",
            "registry..com/app:v1",
            "app;rm -rf /",
            "app:tag\ninjection",
            "../app:latest"
    })
    void shouldRejectInvalidReferences(String reference) {
        assertFalse(DockerImageReference.isValid(reference));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "nginx:alpine",
            "localhost:5000/api:v1",
            "ghcr.io/usuario/app:latest"
    })
    void shouldExtractImageNameAndTag(String reference) {
        DockerImageReference parsed =
                DockerImageReference.parse(reference);

        assertNotNull(parsed.name());
        assertNotNull(parsed.tag());
    }
}
