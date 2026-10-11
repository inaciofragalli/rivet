
package io.github.inaciofragalli.rivet.image;

import io.github.inaciofragalli.rivet.image.internal.DockerImageInspector;
import io.github.inaciofragalli.rivet.image.internal.ImageArchiveStorage;
import io.github.inaciofragalli.rivet.image.internal.ImageArtifact;
import io.github.inaciofragalli.rivet.image.internal.ImageArtifactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeleteArchiveWhenPersistenceFails() throws IOException {
        ImageArchiveStorage storage =
                new ImageArchiveStorage(tempDir.toString());

        DockerImageInspector inspector = mock(DockerImageInspector.class);
        ImageArtifactRepository repository =
                mock(ImageArtifactRepository.class);

        ImageMetadata metadata = new ImageMetadata(
                "nginx",
                "alpine",
                "amd64",
                "linux",
                List.of(80)
        );

        when(inspector.inspect(any(Path.class)))
                .thenReturn(metadata);

        when(repository.saveAndFlush(any(ImageArtifact.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Simulated database failure"
                ));

        ImageArtifactService service = new ImageArtifactService(
                storage,
                inspector,
                repository
        );

        byte[] content = "fake archive"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.upload(new ByteArrayInputStream(content))
        );

        verify(repository).saveAndFlush(any(ImageArtifact.class));

        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(
                    0,
                    files.count(),
                    "O arquivo deve ser removido quando a persistência falha"
            );
        }
    }
}
