package io.github.inaciofragalli.rivet.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationControllerIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    private Application savedApp;

    @Nested
    class CreateApplication {
        @Test
        @WithMockUser
        void shouldReturnConflictWhenApplicationNameAlreadyExists() throws Exception {
            String requestBody = """
                {
                  "name": "taskhub",
                  "image": "ghcr.io/inaciofragalli/taskhub:v1",
                  "containerPort": 8080
                }
                """;

            mockMvc.perform(post("/api/applications")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpectAll(
                            status().isCreated(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    );

            mockMvc.perform(post("/api/applications")
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpectAll(
                            status().isConflict(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                            jsonPath("$.title").value("Application already exists")
                    );
        }

        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("invalidApplications")
        @WithMockUser
        void shouldReturnBadRequestWhenApplicationDataIsInvalid(
                String scenario,
                String requestBody
        ) throws Exception {

            mockMvc.perform(post("/api/applications")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpectAll(
                            status().isBadRequest(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    );
        }

        private static Stream<Arguments> invalidApplications() {
            return Stream.of(
                    Arguments.of("Nome vazio", """
                        {
                          "name": "",
                          "image": "taskhub:v1",
                          "containerPort": 8080
                        }
                        """),

                    Arguments.of("Nome muito longo", """
                        {
                          "name": "abcdefghijklmnopqrstuvwxyzabcde",
                          "image": "taskhub:v1",
                          "containerPort": 8080
                        }
                        """),

                    Arguments.of("Imagem vazia", """
                        {
                          "name": "taskhub",
                          "image": "",
                          "containerPort": 8080
                        }
                        """),

                    Arguments.of("Porta zero", """
                        {
                          "name": "taskhub",
                          "image": "taskhub:v1",
                          "containerPort": 0
                        }
                        """),

                    Arguments.of("Porta acima do limite", """
                        {
                          "name": "taskhub",
                          "image": "taskhub:v1",
                          "containerPort": 65536
                        }
                        """)
            );
        }
    }

    @Nested
    class GetApplication {
        @BeforeEach
        void createApplication() {
            Application app = new Application("Apocrifo", "apocrifo:v1", 3444);
            Application app2 = new Application("Workers", "workers:v1", 3454);

            savedApp = applicationRepository.save(app);
            applicationRepository.save(app2);
        }

        @Test
        @WithMockUser
        void shouldReturnApplicationWhenExists() throws Exception {
            mockMvc.perform(get("/api/applications/" + savedApp.getId())
                        .with(csrf()))
                    .andExpectAll(
                            status().isOk(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            jsonPath("$.name").value(savedApp.getName()),
                            jsonPath("$.image").isNotEmpty(),
                            jsonPath("$.containerPort").isNotEmpty()
                    );
        }

        @Test
        @WithMockUser
        void shouldReturnNotFoundWhenApplicationNotFound() throws Exception {
            mockMvc.perform(get("/api/applications/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                    .andExpectAll(
                            status().isNotFound(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    );
        }

        @Test
        @WithMockUser
        void shouldReturnAllApplications() throws Exception {
            mockMvc.perform(get("/api/applications"))
                    .andExpectAll(
                            status().isOk(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            jsonPath("$.length()").value(2)
                    );
        }
    }
}