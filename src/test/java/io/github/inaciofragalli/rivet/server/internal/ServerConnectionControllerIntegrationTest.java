package io.github.inaciofragalli.rivet.server.internal;

import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import io.github.inaciofragalli.rivet.server.internal.ssh.MinaSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ServerConnectionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServerRepository serverRepository;

    // Não abre uma conexão SSH real: testa o endpoint e sua integração com o serviço.
    @MockitoBean
    private MinaSshClient minaSshClient;

    private UUID serverId;

    @BeforeEach
    void setUp() {
        serverRepository.deleteAll();

        Server server = serverRepository.saveAndFlush(
                new Server("ssh-test-host", "192.0.2.10", 22, "deploy")
        );
        serverId = server.getId();
    }

    @Test
    void shouldReturnNoContentWhenSshConnectionSucceeds() throws Exception {
        mockMvc.perform(post("/api/servers/{id}/test-connection", serverId)
                        .with(user("admin"))
                        .with(csrf()))
                .andExpectAll(
                        status().isNoContent(),
                        content().string("")
                );

        verify(minaSshClient).verifyConnection(any());
    }

    @Test
    void shouldReturnBadGatewayWithoutLeakingSshDetails() throws Exception {
        String internalMessage = "Private key at /secrets/ssh/id_ed25519 rejected";
        doThrow(new IOException(internalMessage))
                .when(minaSshClient).verifyConnection(any());

        mockMvc.perform(post("/api/servers/{id}/test-connection", serverId)
                        .with(user("admin"))
                        .with(csrf()))
                .andExpectAll(
                        status().isBadGateway(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                        jsonPath("$.title").value("SSH connection failed"),
                        content().string(not(containsString(internalMessage)))
                );
    }

    @Test
    void shouldReturnNotFoundForUnknownServer() throws Exception {
        mockMvc.perform(post("/api/servers/{id}/test-connection", UUID.randomUUID())
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(minaSshClient);
    }

    @Test
    void shouldRejectAnonymousConnectionTest() throws Exception {
        mockMvc.perform(post("/api/servers/{id}/test-connection", serverId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(minaSshClient);
    }

    @Test
    void shouldRejectAuthenticatedConnectionTestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/servers/{id}/test-connection", serverId)
                        .with(user("admin")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(minaSshClient);
    }
}
