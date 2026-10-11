package io.github.inaciofragalli.rivet.application.internal;

import io.github.inaciofragalli.rivet.application.ApplicationResponse;
import io.github.inaciofragalli.rivet.application.ApplicationService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService applicationService;

    @PostMapping()
    public ResponseEntity<ApplicationResponse> createApplication(@Valid @RequestBody CreateApplicationRequest req) {
        ApplicationResponse res;

        if (req.imageArtifactId() != null) {
            res = applicationService.registerFromArtifact(
                    req.name(),
                    req.imageArtifactId(),
                    req.containerPort()
            );
        } else {
            res = applicationService.register(
                    req.name(),
                    req.image(),
                    req.containerPort()
            );
        }

        return ResponseEntity.created(URI.create("/api/applications/" + res.id())).body(res);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@NonNull @PathVariable UUID id) {
        ApplicationResponse res = applicationService.findById(id);

        return ResponseEntity.ok(res);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> getAllApplications() {
        List<ApplicationResponse> res = applicationService.findAll();

        return ResponseEntity.ok(res);
    }
}
