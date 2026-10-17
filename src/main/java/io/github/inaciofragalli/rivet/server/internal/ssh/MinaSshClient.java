package io.github.inaciofragalli.rivet.server.internal.ssh;

import io.github.inaciofragalli.rivet.server.ServerResponse;
import lombok.RequiredArgsConstructor;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MinaSshClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);

    private final SshClient sshClient;

    public void verifyConnection(ServerResponse server) throws IOException {
        try (ClientSession session = sshClient.connect(
                server.sshUsername(),
                server.host(),
                server.sshPort()
        ).verify(CONNECT_TIMEOUT).getSession()) {
            session.auth().verify(AUTH_TIMEOUT);
        }
    }
}
