package io.github.inaciofragalli.rivet.image.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Component
public class ImageArchiveStorage {
    private static final long MAX_ARCHIVE_SIZE = 512L * 1024 * 1024;
    private final Path storageDirectory;
    private static final Logger log = LoggerFactory.getLogger(ImageArchiveStorage.class);

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
                    throw new ImageArchiveTooLargeException();
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

    @EventListener(ApplicationReadyEvent.class)
    public void cleanUpStaleFiles() {
        if (!Files.isDirectory(storageDirectory)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(2));

        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(
                             storageDirectory, "image-*.tmp"
                     )) {

            for (Path file : files) {
                try {
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            && Files.getLastModifiedTime(
                            file, LinkOption.NOFOLLOW_LINKS
                    ).toInstant().isBefore(cutoff)) {

                        Files.deleteIfExists(file);
                        log.info("Arquivo temporário removido: {}",
                                file.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("Falha ao limpar arquivo: {}", file, e);
                }
            }

        } catch (IOException | DirectoryIteratorException e) {
            log.warn("Falha ao verificar arquivos temporários", e);
        }
    }
}
