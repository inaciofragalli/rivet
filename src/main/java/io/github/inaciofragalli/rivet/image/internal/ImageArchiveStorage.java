
package io.github.inaciofragalli.rivet.image.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class ImageArchiveStorage {
    private static final long MAX_ARCHIVE_SIZE = 512L * 1024 * 1024;
    private final Path storageDirectory;

    public ImageArchiveStorage(
            @Value("${rivet.images.storage-dir}") String storageDir
    ) {
        this.storageDirectory = Path.of(storageDir)
                .toAbsolutePath()
                .normalize();
    }

    public Path stage(InputStream input) throws IOException {
        Files.createDirectories(storageDirectory);

        Path temporaryFile = Files.createTempFile(
                storageDirectory,
                "image-",
                ".tmp"
        );

        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                totalBytes += bytesRead;

                if (totalBytes > MAX_ARCHIVE_SIZE) {
                    throw new IOException("Imagem excede o limite de 512 MiB");
                }

                output.write(buffer, 0, bytesRead);
            }

            if (totalBytes == 0) {
                throw new IOException("Arquivo vazio");
            }

            return temporaryFile;

        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporaryFile);
            throw e;
        }
    }

    public Path promote(Path temporaryFile, UUID artifactId)
            throws IOException {

        Path source = temporaryFile.toAbsolutePath().normalize();

        if (!source.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException(
                    "Arquivo temporário fora do diretório de armazenamento"
            );
        }

        Path destination = storageDirectory.resolve(
                artifactId + ".archive"
        );

        return Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    public void delete(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();

        if (!normalized.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException(
                    "Arquivo fora do diretório de armazenamento"
            );
        }

        Files.deleteIfExists(normalized);
    }
}
