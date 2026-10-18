package io.github.inaciofragalli.rivet.deployment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DeploymentNotFoundException extends RuntimeException {
    public DeploymentNotFoundException(UUID id) {
        super("Deployment with id " + id.toString() + " not found");
    }

    public DeploymentNotFoundException(String message) { super(message); }
}
