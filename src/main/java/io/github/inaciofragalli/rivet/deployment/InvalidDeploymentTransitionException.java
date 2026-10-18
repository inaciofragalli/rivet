package io.github.inaciofragalli.rivet.deployment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidDeploymentTransitionException extends RuntimeException {
    public InvalidDeploymentTransitionException() {
        super("Invalid deployment status transition");
    }
}
