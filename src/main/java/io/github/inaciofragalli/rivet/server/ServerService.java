package io.github.inaciofragalli.rivet.server;

import io.github.inaciofragalli.rivet.server.internal.Server;
import io.github.inaciofragalli.rivet.server.internal.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServerService {
    private final ServerRepository serverRepository;

    @Transactional
    public ServerResponse register(
            String name,
            String host,
            Integer sshPort,
            String sshUsername
    ) {
        if (serverRepository.existsByName(name)) {
            throw new ServerAlreadyExistsException(name);
        }

        Server server = new Server(name, host, sshPort, sshUsername);
        serverRepository.save(server);

        return toResponse(server);
    }

    public ServerResponse findById(UUID id) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new ServerNotFoundException(id.toString()));

        return toResponse(server);
    }

    public List<ServerResponse> findAll() {
        List<Server> servers = serverRepository.findAll();

        return servers.stream().map(this::toResponse).toList();
    }

    private ServerResponse toResponse(Server server) {
        return new  ServerResponse(
                server.getId(),
                server.getName(),
                server.getHost(),
                server.getSshPort(),
                server.getSshUsername()
        );
    }
}
