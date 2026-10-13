package io.github.inaciofragalli.rivet.server.internal;

import com.jayway.jsonpath.JsonPath;
import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class ServerControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServerRepository serverRepository;

    private Server savedServer;


    @BeforeEach
    void setUp() {
        serverRepository.deleteAll();
    }

    private String createServer(String name) throws Exception {
        String body = """
            {
                "name": "%s",
                "host": "192.168.1.100",
                "sshPort": 22,
                "sshUsername": "ubuntu"
            }
            """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/servers")
                        .with(user("test-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
    }

    @Nested
    @WithMockUser
    class CreateServer {
        @ParameterizedTest(name = "{index} - {0}")
        @MethodSource("invalidServers")
        @WithMockUser
        void shouldReturnBadRequestWhenServerDataIsInvalid(
                String scenario,
                String requestBody
        ) throws Exception {
            mockMvc.perform(post("/api/servers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpectAll(
                            status().isBadRequest(),
                            content().contentTypeCompatibleWith(
                                    MediaType.APPLICATION_PROBLEM_JSON
                            )
                    );
        }

        private static Stream<Arguments> invalidServers() {
            return Stream.of(
                    Arguments.of("Nome vazio", """
                {
                    "name": "",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Nome muito longo", """
                {
                    "name": "abcdefghijklmnopqrstuvwxyzabcde",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Host vazio", """
                {
                    "name": "homelab",
                    "host": "",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Host muito longo", """
                {
                    "name": "homelab",
                    "host": "%s",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """.formatted("a".repeat(256))),

                    Arguments.of("Porta zero", """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 0,
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Porta acima do limite", """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 65536,
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Porta ausente", """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshUsername": "ubuntu"
                }
                """),

                    Arguments.of("Usuário SSH vazio", """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": ""
                }
                """),

                    Arguments.of("Usuário SSH muito longo", """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "abcdefghijklmnopqrstuvwxyzabcde"
                }
                """)
            );
        }

        @Test
        void shouldCreateServerSuccessfully() throws Exception {
            String body = """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """;

            mockMvc.perform(post("/api/servers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpectAll(
                            status().isCreated(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            header().exists("Location"),
                            jsonPath("$.id").isNotEmpty(),
                            jsonPath("$.name").value("homelab"),
                            jsonPath("$.host").value("192.168.1.100"),
                            jsonPath("$.sshPort").value(22),
                            jsonPath("$.sshUsername").value("ubuntu")
                    );
        }

        @Test
        void shouldReturnConflictWhenServerNameAlreadyExists() throws Exception {
            String body = """
                {
                    "name": "homelab",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """;

            mockMvc.perform(post("/api/servers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/servers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpectAll(
                            status().isConflict(),
                            content().contentTypeCompatibleWith(
                                    MediaType.APPLICATION_PROBLEM_JSON
                            )
                    );
        }

        @Test
        void shouldHandleConcurrentRegistrationsWithSameName() throws Exception {
            String name = "race-" + UUID.randomUUID()
                    .toString().substring(0, 8);

            String body = """
                {
                    "name": "%s",
                    "host": "192.168.1.100",
                    "sshPort": 22,
                    "sshUsername": "ubuntu"
                }
                """.formatted(name);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Callable<Integer> register = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timeout");
                }

                return mockMvc.perform(post("/api/servers")
                                .with(user("test-user"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Integer> first = executor.submit(register);
                Future<Integer> second = executor.submit(register);

                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();

                List<Integer> statuses = Stream.of(
                                first.get(30, TimeUnit.SECONDS),
                                second.get(30, TimeUnit.SECONDS)
                        )
                        .sorted()
                        .toList();

                assertEquals(List.of(201, 409), statuses);
            }

            long count = serverRepository.findAll().stream()
                    .filter(server -> name.equals(server.getName()))
                    .count();

            assertEquals(1, count);
        }
    }

    @Nested
    @WithMockUser
    class GetServer {
        @Test
        void shouldReturnServerWhenExists() throws Exception {
            String id = createServer("homelab");

            mockMvc.perform(get("/api/servers/{id}", id))
                    .andExpectAll(
                            status().isOk(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            jsonPath("$.id").value(id),
                            jsonPath("$.name").value("homelab"),
                            jsonPath("$.host").value("192.168.1.100"),
                            jsonPath("$.sshPort").value(22),
                            jsonPath("$.sshUsername").value("ubuntu")
                    );
        }

        @Test
        void shouldReturnNotFoundWhenServerDoesNotExist() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(get("/api/servers/{id}", id))
                    .andExpectAll(
                            status().isNotFound(),
                            content().contentTypeCompatibleWith(
                                    MediaType.APPLICATION_PROBLEM_JSON
                            )
                    );
        }

        @Test
        void shouldReturnBadRequestWhenIdIsMalformed() throws Exception {
            mockMvc.perform(get("/api/servers/not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnAllServers() throws Exception {
            createServer("homelab");
            createServer("backup");

            mockMvc.perform(get("/api/servers"))
                    .andExpectAll(
                            status().isOk(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            jsonPath("$.length()").value(2),
                            jsonPath("$[*].name").value(
                                    hasItems("homelab", "backup")
                            )
                    );
        }

        @Test
        void shouldReturnEmptyListWhenNoServersExist() throws Exception {
            mockMvc.perform(get("/api/servers"))
                    .andExpectAll(
                            status().isOk(),
                            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                            jsonPath("$.length()").value(0)
                    );
        }
    }
}
