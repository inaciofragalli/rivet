
package io.github.inaciofragalli.rivet.application.internal;

import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import io.github.inaciofragalli.rivet.image.ImageMetadata;
import io.github.inaciofragalli.rivet.image.internal.ImageArtifact;
import io.github.inaciofragalli.rivet.image.internal.ImageArtifactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@WithMockUser
class ApplicationImageArtifactIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ImageArtifactRepository imageArtifactRepository;

    @AfterEach
    void cleanUp() {
        // A FK exige remover as aplicações primeiro.
        applicationRepository.deleteAll();
        imageArtifactRepository.deleteAll();
    }

    @Test
    void shouldRegisterApplicationFromArtifact() throws Exception {
        UUID artifactId = createArtifact();

        String body = """
                {
                  "name": "nginx_upload-1",
                  "imageArtifactId": "%s",
                  "containerPort": 80
                }
                """.formatted(artifactId);

        mockMvc.perform(post("/api/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.name").value("nginx_upload-1"),
                        jsonPath("$.image").value("nginx:alpine"),
                        jsonPath("$.containerPort").value(80),
                        jsonPath("$.imageArtifactId")
                                .value(artifactId.toString())
                );

        assertEquals(1, applicationRepository.count());

        Application application = applicationRepository.findAll().getFirst();

        assertEquals(artifactId, application.getImageArtifactId());
        assertEquals("nginx:alpine", application.getImage());
    }

    @Test
    void shouldReturnNotFoundForUnknownArtifact() throws Exception {
        UUID unknownId = UUID.randomUUID();

        String body = """
                {
                  "name": "unknown-image",
                  "imageArtifactId": "%s",
                  "containerPort": 80
                }
                """.formatted(unknownId);

        mockMvc.perform(post("/api/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.title")
                                .value("Image artifact not found")
                );

        assertEquals(0, applicationRepository.count());
    }

    @Test
    void shouldRejectBothImageSources() throws Exception {
        UUID artifactId = createArtifact();

        String body = """
                {
                  "name": "conflicting-image",
                  "image": "nginx:latest",
                  "imageArtifactId": "%s",
                  "containerPort": 80
                }
                """.formatted(artifactId);

        mockMvc.perform(post("/api/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(0, applicationRepository.count());
    }

    @Test
    void shouldRejectMissingImageSource() throws Exception {
        String body = """
                {
                  "name": "missing-image",
                  "containerPort": 80
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(0, applicationRepository.count());
    }

    @Test
    void shouldPreserveManualRegistration() throws Exception {
        String body = """
                {
                  "name": "manual-app",
                  "image": "ghcr.io/example/app:v1",
                  "containerPort": 8080
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.image")
                                .value("ghcr.io/example/app:v1")
                );

        Application application = applicationRepository.findAll().getFirst();

        assertNull(application.getImageArtifactId());
    }

    private UUID createArtifact() {
        UUID id = UUID.randomUUID();

        ImageMetadata metadata = new ImageMetadata(
                "nginx",
                "alpine",
                "amd64",
                "linux",
                List.of(80)
        );

        ImageArtifact artifact = new ImageArtifact(
                id,
                metadata,
                1024,
                "a".repeat(64)
        );

        imageArtifactRepository.saveAndFlush(artifact);

        return id;
    }
}
