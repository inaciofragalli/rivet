package io.github.inaciofragalli.rivet.server.internal;

import io.github.inaciofragalli.rivet.server.ServerConnectionService;
import io.github.inaciofragalli.rivet.server.ServerResponse;
import io.github.inaciofragalli.rivet.server.ServerService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {
    private final ServerService serverService;
    private final ServerConnectionService serverConnectionService;

    @PostMapping
    public ResponseEntity<ServerResponse> createServer(@Valid @RequestBody CreateServerRequest req) {
        ServerResponse res = serverService.register(
                req.name(),
                req.host(),
                req.sshPort(),
                req.sshUsername()
        );

        return ResponseEntity.created(URI.create("/api/servers/" + res.id())).body(res);
    }

    @GetMapping("/{id}")
    public  ResponseEntity<ServerResponse> getServer(@NonNull @PathVariable UUID id) {
        ServerResponse res = serverService.findById(id);

        return ResponseEntity.ok(res);
    }

    @GetMapping
    public ResponseEntity<List<ServerResponse>> getAllServers() {
        List<ServerResponse> res = serverService.findAll();

        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Void> testConnection(@PathVariable UUID id) {
        serverConnectionService.verifyConnection(id);

        return ResponseEntity.noContent().build();
    }
}