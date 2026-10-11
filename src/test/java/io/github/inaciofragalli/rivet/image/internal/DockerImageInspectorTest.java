
package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageMetadata;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DockerImageInspectorTest {

    private final DockerImageInspector inspector = new DockerImageInspector();

    @TempDir
    Path tempDir;

    @Test
    void shouldInspectImageMetadata() throws IOException {
        Path archive = tempDir.resolve("nginx.tar");

        String configPath = "blobs/sha256/config";

        String config = """
                {
                  "architecture": "amd64",
                  "os": "linux",
                  "config": {
                    "ExposedPorts": {
                      "80/tcp": {}
                    }
                  }
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(configPath, config);
        entries.put("manifest.json", manifest("nginx:alpine", configPath));

        createTar(archive, entries);

        ImageMetadata result = inspector.inspect(archive);

        assertEquals(
                new ImageMetadata("nginx", "alpine", "amd64", "linux", List.of(80)),
                result
        );
    }

    @Test
    void shouldReturnEmptyPortsWhenNotExposed() throws IOException {
        Path archive = tempDir.resolve("hello-world.tar");

        String configPath = "blobs/sha256/config";

        String config = """
                {
                  "architecture": "amd64",
                  "os": "linux",
                  "config": {}
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(configPath, config);
        entries.put("manifest.json", manifest("hello-world:latest", configPath));

        createTar(archive, entries);

        ImageMetadata result = inspector.inspect(archive);

        assertEquals(List.of(), result.exposedPorts());
        assertEquals("hello-world", result.image());
        assertEquals("latest", result.tag());
    }

    @Test
    void shouldIgnoreUdpPorts() throws IOException {
        Path archive = tempDir.resolve("ports.tar");

        String configPath = "config.json";

        String config = """
                {
                  "architecture": "amd64",
                  "os": "linux",
                  "config": {
                    "ExposedPorts": {
                      "80/tcp": {},
                      "53/udp": {},
                      "443/tcp": {}
                    }
                  }
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(configPath, config);
        entries.put("manifest.json", manifest("web:v1", configPath));

        createTar(archive, entries);

        ImageMetadata result = inspector.inspect(archive);

        assertEquals(List.of(80, 443), result.exposedPorts());
    }

    @Test
    void shouldHandleRegistryWithPort() throws IOException {
        Path archive = tempDir.resolve("registry.tar");

        String configPath = "config.json";

        String config = """
                {
                  "architecture": "amd64",
                  "os": "linux",
                  "config": {}
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(configPath, config);
        entries.put(
                "manifest.json",
                manifest("localhost:5000/taskhub:v1", configPath)
        );

        createTar(archive, entries);

        ImageMetadata result = inspector.inspect(archive);

        assertEquals("localhost:5000/taskhub", result.image());
        assertEquals("v1", result.tag());
    }

    @Test
    void shouldThrowWhenManifestIsMissing() throws IOException {
        Path archive = tempDir.resolve("no-manifest.tar");

        createTar(archive, Map.of(
                "some-file.txt", "hello"
        ));

        IOException exception = assertThrows(
                IOException.class,
                () -> inspector.inspect(archive)
        );

        assertEquals("manifest.json não encontrado", exception.getMessage());
    }

    @Test
    void shouldThrowWhenConfigIsMissing() throws IOException {
        Path archive = tempDir.resolve("no-config.tar");

        createTar(archive, Map.of(
                "manifest.json",
                manifest("nginx:alpine", "missing-config.json")
        ));

        IOException exception = assertThrows(
                IOException.class,
                () -> inspector.inspect(archive)
        );

        assertEquals(
                "Arquivo de configuração da imagem não encontrado",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectOversizedManifest() throws IOException {
        Path archive = tempDir.resolve("large-manifest.tar");

        createTar(archive, Map.of(
                "manifest.json", "x".repeat(1024 * 1024 + 1)
        ));

        assertThrows(
                IOException.class,
                () -> inspector.inspect(archive)
        );
    }

    @Test
    void shouldRejectOversizedConfig() throws IOException {
        Path archive = tempDir.resolve("large-config.tar");

        String configPath = "config.json";

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(configPath, "x".repeat(1024 * 1024 + 1));
        entries.put("manifest.json", manifest("nginx:alpine", configPath));

        createTar(archive, entries);

        assertThrows(
                IOException.class,
                () -> inspector.inspect(archive)
        );
    }

    private static String manifest(String image, String configPath) {
        return """
                [
                  {
                    "Config": "%s",
                    "RepoTags": ["%s"],
                    "Layers": []
                  }
                ]
                """.formatted(configPath, image);
    }

    private static void createTar(
            Path archivePath,
            Map<String, String> entries
    ) throws IOException {

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
                Files.newOutputStream(archivePath))) {

            for (Map.Entry<String, String> file : entries.entrySet()) {
                byte[] content = file.getValue()
                        .getBytes(StandardCharsets.UTF_8);

                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(content.length);

                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }

            tar.finish();
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "{invalid json",
            "[]",
            "[{}, {}]",
            "[{\"Config\":\"config.json\"}]",
            "[{\"RepoTags\":[\"app:v1\"]}]"
    })
    void shouldRejectInvalidManifest(String manifestJson) throws IOException {
        Path archive = tempDir.resolve("invalid-manifest.tar");

        createTar(archive, Map.of(
                "manifest.json", manifestJson
        ));

        assertThrows(IOException.class, () -> inspector.inspect(archive));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0/tcp",
            "70000/tcp",
            "abc/tcp",
            "80",
            "80/tcp/extra"
    })
    void shouldRejectInvalidPort(String port) throws IOException {
        Path archive = tempDir.resolve("invalid-port.tar");

        String config = """
            {
              "architecture": "amd64",
              "os": "linux",
              "config": {
                "ExposedPorts": {
                  "%s": {}
                }
              }
            }
            """.formatted(port);

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("config.json", config);
        entries.put("manifest.json", manifest("app:v1", "config.json"));

        createTar(archive, entries);

        assertThrows(IOException.class, () -> inspector.inspect(archive));
    }

    @Test
    void shouldRejectMissingArchitecture() throws IOException {
        Path archive = tempDir.resolve("invalid-config.tar");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("config.json", """
            {
              "os": "linux",
              "config": {}
            }
            """);
        entries.put("manifest.json", manifest("app:v1", "config.json"));

        createTar(archive, entries);

        assertThrows(IOException.class, () -> inspector.inspect(archive));
    }

    @Test
    void shouldInspectGzipCompressedTar() throws IOException {
        Path tarPath = tempDir.resolve("nginx.tar");
        Path gzipPath = tempDir.resolve("nginx.tar.gz");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("config.json", """
            {
              "architecture": "amd64",
              "os": "linux",
              "config": {
                "ExposedPorts": {
                  "80/tcp": {}
                }
              }
            }
            """);
        entries.put("manifest.json", manifest("nginx:alpine", "config.json"));

        createTar(tarPath, entries);

        try (GZIPOutputStream gzip =
                     new GZIPOutputStream(Files.newOutputStream(gzipPath))) {
            Files.copy(tarPath, gzip);
        }

        ImageMetadata metadata = inspector.inspect(gzipPath);

        assertEquals("nginx", metadata.image());
        assertEquals("alpine", metadata.tag());
        assertEquals(List.of(80), metadata.exposedPorts());
    }

}
