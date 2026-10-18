package io.github.inaciofragalli.rivet.deployment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DeploymentAlreadyInProgressException extends RuntimeException {
    public DeploymentAlreadyInProgressException() {
        super("A deployment is already in progress for this application and server");
    }
}
