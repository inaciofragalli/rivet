package io.github.inaciofragalli.rivet.server;

import io.github.inaciofragalli.rivet.server.internal.ssh.MinaSshClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerConnectionServiceTest {

    @Mock
    private ServerService serverService;

    @Mock
    private MinaSshClient minaSshClient;

    @InjectMocks
    private ServerConnectionService connectionService;

    private static final UUID SERVER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    private final ServerResponse server = new ServerResponse(
            SERVER_ID, "homelab", "192.0.2.10", 22, "deploy"
    );

    @Test
    void shouldVerifyConnectionForRegisteredServer() throws Exception {
        when(serverService.findById(SERVER_ID)).thenReturn(server);

        connectionService.verifyConnection(SERVER_ID);

        verify(serverService).findById(SERVER_ID);
        verify(minaSshClient).verifyConnection(server);
    }

    @Test
    void shouldWrapSshIoFailurePreservingCause() throws Exception {
        IOException cause = new IOException("Authentication failed");
        when(serverService.findById(SERVER_ID)).thenReturn(server);
        doThrow(cause).when(minaSshClient).verifyConnection(server);

        ServerConnectionException exception = assertThrows(
                ServerConnectionException.class,
                () -> connectionService.verifyConnection(SERVER_ID)
        );

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldNotConnectWhenServerDoesNotExist() {
        ServerNotFoundException notFound =
                new ServerNotFoundException(SERVER_ID.toString());
        when(serverService.findById(SERVER_ID)).thenThrow(notFound);

        ServerNotFoundException exception = assertThrows(
                ServerNotFoundException.class,
                () -> connectionService.verifyConnection(SERVER_ID)
        );

        assertSame(notFound, exception);
        verifyNoInteractions(minaSshClient);
    }
}
