package io.github.inaciofragalli.rivet.server;

import io.github.inaciofragalli.rivet.server.internal.ssh.MinaSshClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServerConnectionService {
    private final ServerService serverService;
    private final MinaSshClient minaSshClient;

    public void verifyConnection(UUID serverId) {
        ServerResponse server = serverService.findById(serverId);

        try {
            minaSshClient.verifyConnection(server);
        } catch (IOException e) {
            throw new ServerConnectionException(e);
        }
    }
}
