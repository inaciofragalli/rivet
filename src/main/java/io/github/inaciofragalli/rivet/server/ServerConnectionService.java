package io.github.inaciofragalli.rivet.server;

import io.github.inaciofragalli.rivet.server.internal.ssh.MinaSshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Slf4j
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
            log.warn("Falha na conexão SSH com o servidor {}", serverId, e);
            throw new ServerConnectionException(e);
        }
    }
}
