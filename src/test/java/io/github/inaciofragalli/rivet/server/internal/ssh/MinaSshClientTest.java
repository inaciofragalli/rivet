package io.github.inaciofragalli.rivet.server.internal.ssh;

import io.github.inaciofragalli.rivet.server.ServerResponse;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinaSshClientTest {

    @Mock
    private SshClient sshClient;

    @Mock
    private ConnectFuture connectFuture;

    @Mock
    private ClientSession session;

    @Mock
    private AuthFuture authFuture;

    private final ServerResponse server = new ServerResponse(
            UUID.randomUUID(), "homelab", "192.0.2.10", 22, "deploy"
    );

    @Test
    void shouldConnectAuthenticateAndCloseSession() throws Exception {
        when(sshClient.connect("deploy", "192.0.2.10", 22))
                .thenReturn(connectFuture);
        when(connectFuture.verify(Duration.ofSeconds(10)))
                .thenReturn(connectFuture);
        when(connectFuture.getSession()).thenReturn(session);
        when(session.auth()).thenReturn(authFuture);
        when(authFuture.verify(Duration.ofSeconds(10))).thenReturn(authFuture);

        new MinaSshClient(sshClient).verifyConnection(server);

        verify(sshClient).connect("deploy", "192.0.2.10", 22);
        verify(session).auth();
        verify(session).close();
    }

    @Test
    void shouldCloseSessionEvenWhenAuthenticationFails() throws Exception {
        when(sshClient.connect("deploy", "192.0.2.10", 22))
                .thenReturn(connectFuture);
        when(connectFuture.verify(any(Duration.class)))
                .thenReturn(connectFuture);
        when(connectFuture.getSession()).thenReturn(session);
        when(session.auth()).thenReturn(authFuture);
        when(authFuture.verify(any(Duration.class)))
                .thenThrow(new SshException("Authentication failed"));

        assertThrows(
                SshException.class,
                () -> new MinaSshClient(sshClient).verifyConnection(server)
        );

        verify(session).close();
    }

    @Test
    void shouldPropagateConnectionFailureBeforeSessionIsOpened() throws Exception {
        when(sshClient.connect("deploy", "192.0.2.10", 22))
                .thenReturn(connectFuture);
        when(connectFuture.verify(any(Duration.class)))
                .thenThrow(new SshException("Connection refused"));

        assertThrows(
                SshException.class,
                () -> new MinaSshClient(sshClient).verifyConnection(server)
        );

        verifyNoInteractions(session);
    }
}
