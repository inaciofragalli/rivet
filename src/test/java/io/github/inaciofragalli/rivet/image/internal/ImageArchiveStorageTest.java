package io.github.inaciofragalli.rivet.image.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ImageArchiveStorageTest {

    @TempDir
    Path tempDir;

    private ImageArchiveStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ImageArchiveStorage(tempDir.toString());
    }

    @Test
    void shouldStageArchive() throws IOException {
        byte[] content = "fake archive content"
                .getBytes(StandardCharsets.UTF_8);

        Path staged = storage.stage(new ByteArrayInputStream(content));

        assertTrue(Files.exists(staged));
        assertEquals(tempDir.toAbsolutePath(), staged.getParent());
        assertArrayEquals(content, Files.readAllBytes(staged));
    }

    @Test
    void shouldPromoteStagedArchive() throws IOException {
        Path staged = storage.stage(
                new ByteArrayInputStream("archive".getBytes(StandardCharsets.UTF_8))
        );

        UUID artifactId = UUID.randomUUID();

        Path promoted = storage.promote(staged, artifactId);

        assertEquals(artifactId + ".archive", promoted.getFileName().toString());
        assertTrue(Files.exists(promoted));
        assertFalse(Files.exists(staged));
        assertEquals("archive", Files.readString(promoted));
    }

    @Test
    void shouldDeleteStoredArchive() throws IOException {
        Path staged = storage.stage(
                new ByteArrayInputStream("archive".getBytes(StandardCharsets.UTF_8))
        );

        storage.delete(staged);

        assertFalse(Files.exists(staged));
    }

    @Test
    void shouldRejectEmptyArchiveAndCleanTemporaryFile() throws IOException {
        assertThrows(
                IOException.class,
                () -> storage.stage(InputStream.nullInputStream())
        );

        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void shouldRejectFilesOutsideStorageDirectory() {
        Path externalFile = tempDir.getParent().resolve("external.tar");

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.promote(externalFile, UUID.randomUUID())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.delete(externalFile)
        );
    }


    @Test
    void shouldRemoveStaleTemporaryFiles() throws IOException {
        Path oldFile = Files.createTempFile(
                tempDir, "image-", ".tmp"
        );

        Files.setLastModifiedTime(
                oldFile,
                FileTime.from(Instant.now().minus(Duration.ofHours(3)))
        );

        storage.cleanUpStaleFiles();

        assertFalse(Files.exists(oldFile));
    }

    @Test
    void shouldPreserveRecentAndPermanentFiles() throws IOException {
        Path recentFile = Files.createTempFile(
                tempDir, "image-", ".tmp"
        );

        Path permanentFile = Files.createFile(
                tempDir.resolve("saved.archive")
        );

        Files.setLastModifiedTime(
                permanentFile,
                FileTime.from(Instant.now().minus(Duration.ofHours(3)))
        );

        storage.cleanUpStaleFiles();

        assertTrue(Files.exists(recentFile));
        assertTrue(Files.exists(permanentFile));
    }

}
