
package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "rivet.images.storage-dir=./build/test-image-artifacts"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ImageArtifactControllerIntegrationTest {

    private static final Path STORAGE_DIRECTORY =
            Path.of("build/test-image-artifacts").toAbsolutePath();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageArtifactRepository repository;

    @AfterEach
    void cleanUp() throws IOException {
        repository.deleteAll();

        if (Files.isDirectory(STORAGE_DIRECTORY)) {
            try (Stream<Path> files = Files.list(STORAGE_DIRECTORY)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    @Test
    @WithMockUser
    void shouldUploadValidDockerImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "nginx.tar",
                "application/x-tar",
                validDockerArchive()
        );

        String response = mockMvc.perform(
                        multipart("/api/image-artifacts")
                                .file(file)
                                .with(csrf())
                )
                .andExpectAll(
                        status().isCreated(),
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        ),
                        jsonPath("$.image").value("nginx"),
                        jsonPath("$.tag").value("alpine"),
                        jsonPath("$.architecture").value("amd64"),
                        jsonPath("$.os").value("linux"),
                        jsonPath("$.exposedPorts[0]").value(80),
                        jsonPath("$.sha256").isNotEmpty()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String idText = com.jayway.jsonpath.JsonPath.read(
                response, "$.id"
        );

        UUID id = UUID.fromString(idText);

        assertTrue(repository.existsById(id));

        assertTrue(Files.exists(
                STORAGE_DIRECTORY.resolve(id + ".archive")
        ));
    }

    @Test
    @WithMockUser
    void shouldRejectEmptyUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.tar",
                "application/x-tar",
                new byte[0]
        );

        mockMvc.perform(
                        multipart("/api/image-artifacts")
                                .file(file)
                                .with(csrf())
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, repository.count());
    }

    @Test
    @WithMockUser
    void shouldRejectInvalidArchiveAndCleanUp() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.tar",
                "application/x-tar",
                "not a Docker image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/image-artifacts")
                                .file(file)
                                .with(csrf())
                )
                .andExpectAll(
                        status().isBadRequest(),
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                );

        assertEquals(0, repository.count());

        try (Stream<Path> files = Files.list(STORAGE_DIRECTORY)) {
            assertEquals(0, files.count());
        }
    }

    private static byte[] validDockerArchive() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (TarArchiveOutputStream tar =
                     new TarArchiveOutputStream(output)) {

            addEntry(tar, "config.json", """
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

            addEntry(tar, "manifest.json", """
                    [
                      {
                        "Config": "config.json",
                        "RepoTags": ["nginx:alpine"],
                        "Layers": []
                      }
                    ]
                    """);

            tar.finish();
        }

        return output.toByteArray();
    }

    private static void addEntry(
            TarArchiveOutputStream tar,
            String name,
            String content
    ) throws IOException {

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);

        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}
