package io.github.inaciofragalli.rivet.image;

import io.github.inaciofragalli.rivet.image.internal.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageArtifactService {
    private final ImageArchiveStorage storage;
    private final DockerImageInspector inspector;
    private final ImageArtifactRepository repository;

    public ImageArtifactResponse upload(InputStream input) throws IOException {
        Path staged = storage.stage(input);
        Path promoted = null;

        try {
            ImageMetadata metadata;

            try {
                metadata = inspector.inspect(staged);
            } catch (ImageArchiveTooLargeException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new InvalidImageArchiveException(e);
            }

            long size = Files.size(staged);
            String checksum = calculateSha256(staged);

            UUID id = UUID.randomUUID();

            promoted = storage.promote(staged, id);

            ImageArtifact artifact = new ImageArtifact(
                    id, metadata, size, checksum
            );

            repository.saveAndFlush(artifact);

            return new ImageArtifactResponse(
                    id,
                    metadata.image(),
                    metadata.tag(),
                    metadata.architecture(),
                    metadata.os(),
                    metadata.exposedPorts(),
                    size,
                    checksum
            );

        } catch (IOException | RuntimeException e) {
            try {
                storage.delete(promoted != null ? promoted : staged);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw e;
        }
    }

    private String calculateSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    public ImageArtifactResponse findById(UUID id) {
        ImageArtifact artifact = repository.findById(id)
                .orElseThrow(() -> new ImageArtifactNotFoundException(id));

        return new ImageArtifactResponse(
                artifact.getId(),
                artifact.getImageName(),
                artifact.getImageTag(),
                artifact.getArchitecture(),
                artifact.getOs(),
                artifact.getExposedPorts(),
                artifact.getArchiveSizeBytes(),
                artifact.getSha256()
        );
    }
}
